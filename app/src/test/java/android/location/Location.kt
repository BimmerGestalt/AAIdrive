package android.location

class Location(val provider: String) {
	var latitude: Double = 0.0
	var longitude: Double = 0.0
	var altitude: Double = 0.0
	var speed: Float = 0f
	var bearing: Float = 0f
}