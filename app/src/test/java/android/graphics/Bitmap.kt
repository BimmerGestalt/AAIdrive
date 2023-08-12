package android.graphics

import android.os.Parcelable
import java.io.OutputStream

abstract class Bitmap: Parcelable {
	enum class CompressFormat(nativeInt: Int) {
		JPEG(0),
		PNG(1),
		WEBP(2),
		WEBP_LOSSY(3),
		WEBP_LOSSLESS(4);
	}

	val width: Int = 0
	val height: Int = 0

	fun compress(format: CompressFormat, flags: Int, output: OutputStream): Boolean = true

	fun sameAs(other: Bitmap): Boolean = false
}