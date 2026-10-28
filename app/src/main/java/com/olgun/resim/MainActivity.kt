package com.olgun.resim

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.*
import java.io.OutputStream
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var editor: Editor
    private var source: Bitmap? = null
    private var output: Bitmap? = null
    private var mode = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: () -> Unit) {
            val b = Button(this).apply { text = label; textSize = 12f; setOnClickListener { action() } }
            bar.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        button("Dosya Aç") { val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }; startActivityForResult(i, 10) }
        button("Basılı Tut") { mode = 0; Toast.makeText(this, "Nesneye uzun basın", Toast.LENGTH_SHORT).show() }
        button("Çiz") { mode = 1; Toast.makeText(this, "Nesnenin çevresini çizin", Toast.LENGTH_SHORT).show() }
        button("Geri Al") { output = null; editor.invalidate() }
        button("Kaydet") { save() }
        root.addView(bar)
        editor = Editor()
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val decoded = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                val scale = min(1f, 2048f / max(decoded.width, decoded.height))
                source = Bitmap.createScaledBitmap(decoded, max(1,(decoded.width*scale).toInt()), max(1,(decoded.height*scale).toInt()), true).copy(Bitmap.Config.ARGB_8888, false)
                output = null; editor.invalidate()
            } catch (e: Exception) { Toast.makeText(this, "Resim açılamadı", Toast.LENGTH_LONG).show() }
        }
    }
    private fun save() {
        val bmp = output ?: return
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Resim_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Resim")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Pictures/Resim klasörüne kaydedildi", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Kaydetme başarısız", Toast.LENGTH_LONG).show() }
    }
    inner class Editor : View(this) {
        private val paint = Paint(3)
        private val path = Path()
        private val points = mutableListOf<PointF>()
        private val handler = Handler(Looper.getMainLooper())
        private var startX = 0f; private var startY = 0f
        private var moved = false; private var fired = false
        private var rect = RectF()
        private var longPress: Runnable? = null
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawColor(Color.rgb(245,245,245))
            val bmp = output ?: source ?: return
            val scale = min(width.toFloat()/bmp.width, height.toFloat()/bmp.height)
            val w = bmp.width*scale; val h = bmp.height*scale
            rect = RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2)
            c.drawBitmap(bmp, null, rect, paint)
            if (mode == 1 && points.isNotEmpty()) {
                paint.color = Color.RED; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                c.drawPath(path, paint); paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            }
        }
        private fun map(x: Float,y: Float): PointF? {
            val bmp = source ?: return null
            if (!rect.contains(x,y)) return null
            return PointF(((x-rect.left)/rect.width()*bmp.width).coerceIn(0f,(bmp.width-1).toFloat()), ((y-rect.top)/rect.height()*bmp.height).coerceIn(0f,(bmp.height-1).toFloat()))
        }
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            if (source == null) return true
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX=e.x; startY=e.y; moved=false; fired=false
                    if(mode==1) { points.clear(); path.reset(); path.moveTo(e.x,e.y); map(e.x,e.y)?.let { points.add(it) }; invalidate() }
                    else {
                        val task = Runnable { if(!moved) { fired=true; map(startX,startY)?.let { flood(it.x.toInt(),it.y.toInt()) } } }
                        longPress=task; handler.postDelayed(task,550)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if(hypot(e.x-startX,e.y-startY)>18) { moved=true; longPress?.let { handler.removeCallbacks(it) } }
                    if(mode==1) { path.lineTo(e.x,e.y); map(e.x,e.y)?.let { points.add(it) }; invalidate() }
                }
                MotionEvent.ACTION_UP -> {
                    longPress?.let { handler.removeCallbacks(it) }
                    if(mode==1 && points.size>=3) { path.close(); polygon(); points.clear(); path.reset(); invalidate() }
                }
                MotionEvent.ACTION_CANCEL -> { longPress?.let { handler.removeCallbacks(it) }; points.clear(); path.reset(); invalidate() }
            }
            return true
        }
        private fun polygon() {
            val src = source ?: return
            val mask = Bitmap.createBitmap(src.width,src.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(mask)
            val p=Path().apply { moveTo(points[0].x,points[0].y); for(i in 1 until points.size) lineTo(points[i].x,points[i].y); close() }
            canvas.drawPath(p,Paint(3).apply { color=Color.BLACK })
            val a=IntArray(src.width*src.height); val m=IntArray(a.size)
            src.getPixels(a,0,src.width,0,0,src.width,src.height)
            mask.getPixels(m,0,src.width,0,0,src.width,src.height)
            for(i in a.indices) if(Color.alpha(m[i])==0) a[i]=Color.WHITE
            output=Bitmap.createBitmap(a,src.width,src.height,Bitmap.Config.ARGB_8888)
        }
        private fun flood(sx:Int,sy:Int) {
            val src=source ?: return; val w=src.width; val h=src.height
            val a=IntArray(w*h); src.getPixels(a,0,w,0,0,w,h)
            val seed=a[sy*w+sx]; val selected=BooleanArray(a.size)
            val queue=IntArray(a.size); var head=0; var tail=0
            val start=sy*w+sx; queue[tail++]=start; selected[start]=true
            fun similar(c:Int):Boolean {
                val dr=Color.red(c)-Color.red(seed); val dg=Color.green(c)-Color.green(seed); val db=Color.blue(c)-Color.blue(seed)
                return dr*dr+dg*dg+db*db < 55*55
            }
            while(head<tail) {
                val i=queue[head++]; val x=i%w; val y=i/w
                fun add(j:Int) { if(!selected[j] && similar(a[j])) { selected[j]=true; queue[tail++]=j } }
                if(x>0) add(i-1); if(x<w-1) add(i+1); if(y>0) add(i-w); if(y<h-1) add(i+w)
            }
            for(i in a.indices) if(!selected[i]) a[i]=Color.WHITE
            output=Bitmap.createBitmap(a,w,h,Bitmap.Config.ARGB_8888)
            invalidate()
        }
    }
}
