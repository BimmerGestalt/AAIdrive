package me.hufman.androidautoidrive.phoneui.fragments

import android.graphics.PixelFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.maps.MapInteractionControllerIntent
import me.hufman.androidautoidrive.databinding.MapTestingBinding
import me.hufman.androidautoidrive.phoneui.controllers.MapTestingController

class MapTestingFragment: Fragment() {
	private val controller by lazy { MapTestingController(
		MapInteractionControllerIntent(this.requireContext()),
		CarInformation.cdsData
	) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = MapTestingBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.controller = controller
		return binding.root
	}

	override fun onResume() {
		super.onResume()

		val surfaceView = requireView().findViewById<SurfaceView>(R.id.imgMapTest)
		surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
		surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
			override fun surfaceCreated(p0: SurfaceHolder) {
				controller.start(requireContext(), p0.surface, surfaceView.width, surfaceView.height)
			}

			override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
				controller.start(requireContext(), p0.surface, surfaceView.width, surfaceView.height)
			}

			override fun surfaceDestroyed(p0: SurfaceHolder) {
				controller.pause(requireContext())
			}
		})

		val surface = surfaceView.holder.surface
		if (surface.isValid) {
			controller.start(requireContext(), surface, surfaceView.width, surfaceView.height)
		}
	}
}