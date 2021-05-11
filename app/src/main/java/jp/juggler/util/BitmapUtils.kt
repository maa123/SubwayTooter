package jp.juggler.util

import android.content.Context
import android.graphics.*
import    androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.annotation.StringRes
//import it.sephiroth.android.library.exif2.ExifInterface
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.sqrt

private val log = LogCategory("BitmapUtils")

fun InputStream.imageOrientation() : Int? =
	try {
		ExifInterface(this)
			//			.readExif(
			//				this@imageOrientation,
			//				ExifInterface.Options.OPTION_IFD_0
			//					or ExifInterface.Options.OPTION_IFD_1
			//					or ExifInterface.Options.OPTION_IFD_EXIF
			//			)
			.getAttributeInt(ExifInterface.TAG_ORIENTATION, - 1)
			.takeIf { it >= 0 }
	} catch(ex : Throwable) {
		log.w(ex, "imageOrientation: exif parse failed.")
		null
	}

// 回転情報の値に合わせて、wとhを入れ替える
fun rotateSize(orientation : Int?, w : Float, h : Float) : PointF =
	when(orientation) {
		5, 6, 7, 8 -> PointF(h, w)
		else -> PointF(w, h)
	}

// 回転情報を解決するようにmatrixに回転を加える
fun Matrix.resolveOrientation(orientation : Int?) : Matrix {
	when(orientation) {
		2 -> postScale(1f, - 1f)
		3 -> postRotate(180f)
		4 -> postScale(- 1f, 1f)
		
		5 -> {
			postScale(1f, - 1f)
			postRotate(- 90f)
		}
		
		6 -> postRotate(90f)
		
		7 -> {
			postScale(1f, - 1f)
			postRotate(90f)
		}
		
		8 -> postRotate(- 90f)
	}
	return this
}

enum class ResizeType {
	// リサイズなし
	None,
	
	// 長辺がsize以下になるようリサイズ
	LongSide,
	
	// 平方ピクセルが size*size 以下になるようリサイズ
	SquarePixel,
}

class ResizeConfig(
	val type : ResizeType,
	val size : Int,
	@StringRes val extraStringId: Int = 0,
){
	val spec :String get() = when(type){
		ResizeType.None -> type.toString()
		else ->"$type,$size"
	}
}

fun createResizedBitmap(
	context : Context,
	uri : Uri,
	sizeLongSide : Int,
	skipIfNoNeedToResizeAndRotate : Boolean = false
) =
	createResizedBitmap(
		context,
		uri,
		if(sizeLongSide <= 0)
			ResizeConfig(ResizeType.None, 0)
		else
			ResizeConfig(ResizeType.LongSide, sizeLongSide),
		skipIfNoNeedToResizeAndRotate = skipIfNoNeedToResizeAndRotate
	)

fun createResizedBitmap(
	context : Context,
	
	// contentResolver.openInputStream に渡すUri
	uri : Uri,
	
	// リサイズ指定
	resizeConfig : ResizeConfig,
	
	// 真の場合、リサイズも回転も必要ないならnullを返す
	skipIfNoNeedToResizeAndRotate : Boolean = false

) : Bitmap? {
	
	try {
		
		val orientation : Int? = context.contentResolver.openInputStream(uri)?.use {
			it.imageOrientation()
		}
		
		// 画像のサイズを調べる
		val options = BitmapFactory.Options()
		options.inJustDecodeBounds = true
		options.inScaled = false
		options.outWidth = 0
		options.outHeight = 0
		context.contentResolver.openInputStream(uri)?.use {
			BitmapFactory.decodeStream(it, null, options)
		}
		
		var src_width = options.outWidth
		var src_height = options.outHeight
		if(src_width <= 0 || src_height <= 0) {
			context.showToast(false, "could not get image bounds.")
			return null
		}
		
		// 回転後のサイズ
		val srcSize = rotateSize(orientation, src_width.toFloat(), src_height.toFloat())
		val aspect = srcSize.x / srcSize.y
		
		/// 出力サイズの計算
		val sizeSpec = resizeConfig.size.toFloat()
		val dstSize : PointF = when(resizeConfig.type) {
			
			ResizeType.None ->
				srcSize
			
			ResizeType.LongSide ->
				if(max(srcSize.x, srcSize.y) <= resizeConfig.size) {
					srcSize
				} else {
					if(aspect >= 1f) {
						PointF(
							resizeConfig.size.toFloat(),
							sizeSpec / aspect
						)
					} else {
						PointF(
							sizeSpec * aspect,
							resizeConfig.size.toFloat()
						)
					}
				}
			
			ResizeType.SquarePixel -> {
				val maxPixels = sizeSpec * sizeSpec
				val currentPixels = srcSize.x * srcSize.y
				if(currentPixels <= maxPixels) {
					srcSize
				} else {
					val y = sqrt(maxPixels / aspect)
					val x = aspect * y
					PointF(x, y)
				}
			}
		}
		
		val dstSizeInt = Point(
			max(1, (dstSize.x + 0.5f).toInt()),
			max(1, (dstSize.y + 0.5f).toInt())
		)
		
		val resizeRequired = dstSizeInt.x != srcSize.x.toInt() || dstSizeInt.y != srcSize.y.toInt()
		
		// リサイズも回転も必要がない場合
		if(skipIfNoNeedToResizeAndRotate
			&& (orientation == null || orientation == 1)
			&& ! resizeRequired
		) {
			log.d("createResizedBitmap: no need to resize or rotate")
			return null
		}
		
		// 長辺
		val dstMax = max(dstSize.x, dstSize.y).toInt()
		
		// inSampleSizeを計算
		var bits = 0
		var x = max(srcSize.x, srcSize.y).toInt()
		while(x > 512 && x > dstMax * 2) {
			++ bits
			x = x shr 1
		}
		options.inJustDecodeBounds = false
		options.inSampleSize = 1 shl bits
		
		val sourceBitmap : Bitmap? =
			context.contentResolver.openInputStream(uri)?.use {
				BitmapFactory.decodeStream(it, null, options)
			}
		
		if(sourceBitmap == null) {
			context.showToast(false, "could not decode image.")
			return null
		}
		try {
			// サンプル数が変化している
			src_width = options.outWidth
			src_height = options.outHeight
			val scale = dstMax.toFloat() / max(src_width, src_height)
			
			val matrix = Matrix().apply {
				reset()
				
				// 画像の中心が原点に来るようにして
				postTranslate(src_width * - 0.5f, src_height * - 0.5f)
				
				// スケーリング
				postScale(scale, scale)
				
				// 回転情報があれば回転
				resolveOrientation(orientation)
				
				// 表示領域に埋まるように平行移動
				postTranslate(dstSizeInt.x.toFloat() * 0.5f, dstSizeInt.y.toFloat() * 0.5f)
			}
			
			// 出力用Bitmap作成
			var dst : Bitmap? =
				Bitmap.createBitmap(dstSizeInt.x, dstSizeInt.y, Bitmap.Config.ARGB_8888)
			try {
				return if(dst == null) {
					context.showToast(false, "bitmap creation failed.")
					null
				} else {
					val canvas = Canvas(dst)
					val paint = Paint()
					paint.isFilterBitmap = true
					canvas.drawBitmap(sourceBitmap, matrix, paint)
					log.d(
						"createResizedBitmap: resized to %sx%s",
						dstSizeInt.x,
						dstSizeInt.y
					)
					val tmp = dst
					dst = null
					tmp
				}
			} finally {
				dst?.recycle()
			}
		} finally {
			sourceBitmap.recycle()
		}
	} catch(ex : FileNotFoundException) {
		log.w(ex, "not found. $uri")
	} catch(ex : SecurityException) {
		log.w(ex, "maybe we need pick up image again.")
	} catch(ex : Throwable) {
		log.trace(ex, "createResizedBitmap")
	}
	return null
}
