#!/usr/bin/env python3

import datetime
import json
import logging
import requests
from math import floor, log10

from localsettings import SENTRY_AUTH_TOKEN, SENTRY_ISSUE_ID


logging.basicConfig(level=logging.INFO)


class ReportGenerator:
    def __init__(self, initial, reducer, formatter):
        self.accumulator = initial
        self.reducer = reducer
        self.formatter = formatter

    def reduce(self, event):
        try:
            replaced = self.reducer(self.accumulator, event)
        except Exception:
            #logging.warning("Failed to reduce event " + str(event))
            raise
        if replaced:
            self.accumulator = replaced

    def output(self):
        return self.formatter(self.accumulator)


def collect_events():
    session = requests.Session()
    session.headers.update({"Authorization": "Bearer " + SENTRY_AUTH_TOKEN})

    url = "https://sentry.io/api/0/issues/{}/events/".format(SENTRY_ISSUE_ID)
    while url is not None:
        logging.info(url.rsplit('/')[-1])
        response = session.get(url)
        data = response.json()
        if not isinstance(data, list):
            logging.error("Unexpected response from Sentry: " + response.text)
            return
        yield from data
        url = response.links.get('next', {}).get('url')


def collect_week_events():
    weekago = datetime.date.today() - datetime.timedelta(days=7)
    weekago_iso = weekago.isoformat()
    for event in collect_events():
        if weekago_iso < event['dateCreated']:
            yield event
        else:
            break


def tags_dict(tags_list):
    output = {}
    for tag in tags_list:
        output[tag['key']] = tag['value']
    output['vehicle_brand'] = output['hmi_type'].split()[0]
    return output


def car_identifier(event):
    tags = tags_dict(event['tags'])
    tags.setdefault('user', None)
    tags.setdefault('vehicle_type', None)
    return "{user}-{vehicle_type}".format_map(tags)


def format_number(i):
    """
    Formats this number to a simple human readable version

    >>> format_number(1234567890)
    '1200m'
    >>> format_number(123456789)
    '120m'
    >>> format_number(12345678)
    '12m'
    >>> format_number(1234567)
    '1.2m'
    >>> format_number(123456)
    '120k'
    >>> format_number(12345)
    '12k'
    >>> format_number(1234)
    '1.2k'
    >>> format_number(123)
    '120'
    >>> format_number(12)
    '12'
    >>> format_number(1)
    '1'
    """
    rounded = int(round(i, 1-int(floor(log10(i)))))
    if rounded > 10000000:  # lol
        return "{:.0f}m".format(rounded / 1000000)
    if rounded > 1000000:
        return "{:.1f}m".format(rounded / 1000000)
    if rounded > 10000:
        return "{:.0f}k".format(rounded / 1000)
    if rounded > 1000:
        return "{:.1f}k".format(rounded / 1000)
    return str(rounded)


REPORTS = {
    "cars.json": ReportGenerator(
        dict(),
        lambda d, e: d.update({car_identifier(e): {k:v
            for (k,v) in tags_dict(e['tags']).items()
            if k in ('vehicle_country', 'vehicle_brand', 'vehicle_type')}}),
        lambda s: json.dumps(list(s.values()))
    ),
    "weekly_users.json": ReportGenerator(
        set(),
        lambda s, e: s.add((e['user'] or {}).get('id')),
        lambda s: json.dumps({
            "schemaVersion": 1,
            "label": "users",
            "message": format_number(len(s)),
            "color": "69f",
            "cacheSeconds": 14400
        })
    )
}


def generate_reports():
    for event in collect_week_events():
        for report in REPORTS.values():
            report.reduce(event)


if __name__ == '__main__':
    generate_reports()
    for name, report in REPORTS.items():
        with open(name, 'w') as output_file:
            output = report.output()
            logging.info(name + ": " + output)
            output_file.write(output)
