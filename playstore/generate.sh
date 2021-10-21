#!/bin/bash

cd "$(dirname "$(realpath "$0")")"

function lang_line {
    name="$1"
    grep -F '<string name="'"$name"'">' "$lang_dir"/strings.xml | sed -e 's/\s*<[^>]*>\s*//g' | head -n 1
}

for lang_dir in res/values*; do
    lang="${lang_dir#res/values}"
    output_name="listing$lang.txt"
    echo "$output_name"
    cat >$output_name <<EOF
Short Description:
`lang_line playstore_tagline`

Full Description:
🚙 `lang_line playstore_desc_title`

`lang_line playstore_desc_features_heading`
🎵 `lang_line playstore_desc_features_music`
🎙️ `lang_line playstore_desc_features_assistants`
🔔 `lang_line playstore_desc_features_notifications`
🗺️ `lang_line playstore_desc_features_carnav`
👀 `lang_line playstore_desc_features_carinfo`
🧩 `lang_line playstore_desc_features_addons_mirroring`
🚧 `lang_line playstore_desc_features_custommap_soon`

✨ `lang_line playstore_desc_nativeapps_protocol`

⚠️ `lang_line playstore_desc_depends_mybmw` `lang_line playstore_desc_depends_bmw_subscription`
EOF
done
