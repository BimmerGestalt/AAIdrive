package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.android.synthetic.main.fragment_car_drivingstats.*
import kotlinx.android.synthetic.main.fragment_supportpage.*
import me.hufman.androidautoidrive.databinding.CarDrivingStatsBinding
import me.hufman.androidautoidrive.phoneui.DonationRequest
import me.hufman.androidautoidrive.phoneui.viewmodels.CarDrivingStatsModel

class CarDrivingStatsFragment: Fragment() {
	val viewModel by activityViewModels<CarDrivingStatsModel>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = CarDrivingStatsBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel

		//val btn_gmap = (Button)findViewById(id);
		if(viewModel.GPSPosition.value != null) {
			btn_gmaps.setOnClickListener {
				val intent = Intent(Intent.ACTION_VIEW).apply {
					//data = Uri.parse(DonationRequest.DONATION_URL)
					val a = viewModel.GPSPosition
					data = Uri.parse("https://www.google.com/maps/?q=car+@" + a.value?.second)
					flags = Intent.FLAG_ACTIVITY_NEW_TASK
				}
				requireContext().startActivity(intent)
			}
		}


		return binding.root
	}
}