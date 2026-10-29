package com.olgun.resim

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.view.*
import android.widget.*
import kotlin.math.*

class MainActivity : Activity() {
 data class Page(var bitmap: Bitmap, var approved:Boolean=false)
 private val pages= mutableListOf<Page>()
 private var current=0
 private lateinit var preview:ImageView
 private var selection: RectF? = null
 private var selectionBitmap: Bitmap? = null
 private lateinit var list:LinearLayout
 private lateinit var info:TextView
 private val margins= mutableListOf<SeekBar>()
 private fun dp(v:Float)= (v*resources.displayMetrics.density).toInt()
 private fun message(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(248,250,252)
  window.navigationBarColor=Color.BLACK
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
  val root=LinearLayout(this).apply { orientation=1;setPadding(dp(2f),dp(.5f),dp(2f),dp(10f));setBackgroundColor(Color.rgb(248,250,252)) }
  val title=TextView(this).apply{text="Pdf  •  Belge Tarayıcı";textSize=20f;setTextColor(Color.rgb(2,132,199));setPadding(dp(12f),dp(12f),0,dp(10f))}
  root.addView(title)
  val actions=LinearLayout(this).apply{orientation=0}
  fun action(parent:LinearLayout,name:String,callback:()->Unit) {
   parent.addView(Button(this).apply {text=name;textSize=11f;isAllCaps=false;setOnClickListener{callback()}},LinearLayout.LayoutParams(0,dp(51f),1f))
  }
  action(actions,"Kamera"){startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),2)}
  action(actions,"Resimler"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)},1)}
  action(actions,"Bitti"){pages.forEach { it.approved=true };refresh();savePdf()}
  root.addView(actions)
  info=TextView(this).apply{setPadding(dp(10f),dp(5f),0,dp(5f));textSize=13f}
  root.addView(info)
  preview=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.WHITE)}
  root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
  val scroller=HorizontalScrollView(this)
  list=LinearLayout(this).apply{orientation=0}
  scroller.addView(list)
  root.addView(scroller,LinearLayout.LayoutParams(-1,dp(58f)))
  val tools=LinearLayout(this)
  action(tools,"Otomatik"){autoCrop()}
  action(tools,"Kırp"){manualCrop()}
  action(tools,"Döndür"){modify{b->val m=Matrix().apply{postRotate(90f)};Bitmap.createBitmap(b,0,0,b.width,b.height,m,true)}}
  action(tools,"Temizle"){clean()}
  val tools2=LinearLayout(this)
  action(tools2,"◀ Sıra"){move(-1)}
  action(tools2,"Sıra ▶"){move(1)}
  action(tools2,"Onayla"){if(pages.isNotEmpty()){pages[current].approved=true;refresh()}}
  action(tools2,"Sil"){if(pages.isNotEmpty()){pages.removeAt(current);current=(current-1).coerceAtLeast(0);refresh()}}
  val footer=TextView(this).apply{text="Muhammed Yusuf OLGUN";gravity=Gravity.CENTER;textSize=12f;setTextColor(Color.LTGRAY);setPadding(0,dp(4f),0,dp(4f))}
  root.addView(footer)
  preview.setOnClickListener { if(pages.isNotEmpty())showOptions() }
  setContentView(root);refresh()
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK)return
  if(requestCode==2){
   val b=data?.extras?.get("data") as? Bitmap
   if(b!=null)add(b) else message("Kamera görüntüsü alınamadı")
  }else if(requestCode==1){
   val uris= mutableListOf<Uri>()
   data?.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)}
   if(uris.isEmpty())data?.data?.let{uris.add(it)}
   for(uri in uris)try{
    contentResolver.openInputStream(uri).use{stream->BitmapFactory.decodeStream(stream)?.let{add(it)}}
   }catch(e:Exception){message("Bir resim okunamadı")}
  }
 }
 private fun add(b:Bitmap){
  val s=min(1f,2000f/max(b.width,b.height))
  pages.add(Page(Bitmap.createScaledBitmap(b,max(1,(b.width*s).toInt()),max(1,(b.height*s).toInt()),true)))
  current=pages.lastIndex;detectSelection();refresh()
 }
 private fun refresh(){
  info.text=if(pages.isEmpty())"Kamera ile çekin veya galeriden resim seçin" else "Sayfa ${current+1} / ${pages.size}  •  ${if(pages[current].approved)"Onaylandı ✓" else "Düzenleniyor"}"
  preview.setImageBitmap(pages.getOrNull(current)?.bitmap?.let { marked(it) })
  list.removeAllViews()
  pages.forEachIndexed{i,p->
   val b=Button(this).apply{text="${i+1}${if(p.approved)" ✓" else ""}";textSize=12f;setOnClickListener{current=i;detectSelection();refresh()}}
   list.addView(b,LinearLayout.LayoutParams(dp(66f),dp(53f)))
  }
 }
 private fun modify(f:(Bitmap)->Bitmap){
  if(pages.isEmpty())return
  try{pages[current].bitmap=f(pages[current].bitmap);pages[current].approved=false;detectSelection();refresh()}catch(e:Exception){message("Düzenleme başarısız")}
 }
 private fun move(d:Int){
  if(pages.isEmpty())return
  val target=current+d
  if(target !in pages.indices)return
  java.util.Collections.swap(pages,current,target);current=target;detectSelection();refresh()
 }
 private fun clean(){
  modify{src->
   val b=src.copy(Bitmap.Config.ARGB_8888,true)
   val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
   for(i in pixels.indices){
    val c=pixels[i];val gray=(Color.red(c)*.299+Color.green(c)*.587+Color.blue(c)*.114).toInt()
    val v=((gray-105)*1.55+105).toInt().coerceIn(0,255)
    pixels[i]=Color.rgb(v,v,v)
   }
   b.setPixels(pixels,0,b.width,0,0,b.width,b.height);b
  }
 }
 private fun crop(l:Float,t:Float,r:Float,b:Float){
  modify{src->
   val x=(src.width*l).toInt().coerceIn(0,src.width-1)
   val y=(src.height*t).toInt().coerceIn(0,src.height-1)
   val right=(src.width*r).toInt().coerceIn(x+1,src.width)
   val bottom=(src.height*b).toInt().coerceIn(y+1,src.height)
   Bitmap.createBitmap(src,x,y,right-x,bottom-y)
  }
 }
 private fun manualCrop(){
  if(pages.isEmpty())return
  val labels=arrayOf("Sol","Üst","Sağ","Alt")
  val panel=LinearLayout(this).apply{orientation=1;setPadding(dp(16f),0,dp(16f),0)}
  val bars=labels.mapIndexed{i,label->
   panel.addView(TextView(this).apply{text=label})
   SeekBar(this).apply{max=100;progress=if(i<2)0 else 100;panel.addView(this)}
  }
  android.app.AlertDialog.Builder(this).setTitle("Kenarları ayarla (%)").setView(panel)
   .setPositiveButton("Kırp"){_,_->
    val a=bars.map{it.progress/100f}
    if(a[2]-a[0]>.05f&&a[3]-a[1]>.05f)crop(a[0],a[1],a[2],a[3])else message("Kenarlar geçersiz")
   }.setNegativeButton("İptal",null).show()
 }
 private fun autoCrop(){
  if(pages.isEmpty())return
  val src=pages[current].bitmap
  // Conservative bright-page bounding box; falls back to original if no reliable edges.
  val step=max(1,max(src.width,src.height)/350)
  var left=src.width;var top=src.height;var right=0;var bottom=0;var count=0
  for(y in 0 until src.height step step)for(x in 0 until src.width step step){
   val c=src.getPixel(x,y);val v=(Color.red(c)+Color.green(c)+Color.blue(c))/3
   if(v>195){left=min(left,x);top=min(top,y);right=max(right,x);bottom=max(bottom,y);count++}
  }
  if(count>15 && right-left>src.width*.45 && bottom-top>src.height*.45 &&
   (right-left)*(bottom-top)<src.width*src.height*.97){
   crop(left.toFloat()/src.width,top.toFloat()/src.height,(right+step).toFloat()/src.width,(bottom+step).toFloat()/src.height)
  }else message("Kenar güvenle belirlenemedi; manuel kırpın")
 }

 private fun showOptions(){
  val names=arrayOf("Otomatik seç ve kırp","Kenarları elle ayarla","Seçimi uygula ve netleştir","Döndür","Temizle","Önceki sıraya taşı","Sonraki sıraya taşı","Sayfayı onayla","Sayfayı sil","İptal")
  android.app.AlertDialog.Builder(this).setTitle("Sayfa işlemleri").setItems(names){_,which->
   when(which){
    0->{detectSelection();refresh()}
    1->manualCrop()
    2->{val r=selection;if(r!=null){crop(r.left,r.top,r.right,r.bottom);clean()}else message("Kenar seçimi bulunamadı")}
    3->modify{b->val m=Matrix().apply{postRotate(90f)};Bitmap.createBitmap(b,0,0,b.width,b.height,m,true)}
    4->clean()
    5->move(-1)
    6->move(1)
    7->{pages[current].approved=true;refresh()}
    8->{pages.removeAt(current);current=(current-1).coerceAtLeast(0);detectSelection();refresh()}
   }
  }.show()
 }
 private fun marked(src:Bitmap):Bitmap{
  val r=selection?:return src
  val b=src.copy(Bitmap.Config.ARGB_8888,true)
  val c=Canvas(b)
  val p=Paint(3).apply{color=Color.rgb(2,132,199);style=Paint.Style.STROKE;strokeWidth=max(3f,src.width/300f)}
  val box=RectF(r.left*src.width,r.top*src.height,r.right*src.width,r.bottom*src.height)
  c.drawRect(box,p)
  p.style=Paint.Style.FILL
  for(x in arrayOf(box.left,box.right))for(y in arrayOf(box.top,box.bottom))c.drawCircle(x,y,max(7f,src.width/150f),p)
  return b
 }
 private fun detectSelection(){
  selection=null
  val src=pages.getOrNull(current)?.bitmap?:return
  val step=max(1,max(src.width,src.height)/350)
  var left=src.width;var top=src.height;var right=0;var bottom=0;var count=0
  for(y in 0 until src.height step step)for(x in 0 until src.width step step){
   val c=src.getPixel(x,y)
   val v=(Color.red(c)+Color.green(c)+Color.blue(c))/3
   if(v>195){left=min(left,x);top=min(top,y);right=max(right,x);bottom=max(bottom,y);count++}
  }
  if(count>15&&right-left>src.width*.45&&bottom-top>src.height*.45&&(right-left)*(bottom-top)<src.width*src.height*.97)
   selection=RectF(left.toFloat()/src.width,top.toFloat()/src.height,((right+step).toFloat()/src.width).coerceAtMost(1f),((bottom+step).toFloat()/src.height).coerceAtMost(1f))
  else selection=RectF(0f,0f,1f,1f)
 }
 private fun savePdf(){
  if(pages.isEmpty()){message("Önce sayfa ekleyin");return}
  if(pages.any{!it.approved}){message("Tüm sayfaları onaylayın");return}
  val doc=PdfDocument()
  try{
   pages.forEachIndexed{i,p->
    val bmp=p.bitmap
    val w=595;val h=842
    val page=doc.startPage(PdfDocument.PageInfo.Builder(w,h,i+1).create())
    page.canvas.drawColor(Color.WHITE)
    val scale=min(w.toFloat()/bmp.width,h.toFloat()/bmp.height)
    val dw=bmp.width*scale;val dh=bmp.height*scale
    page.canvas.drawBitmap(bmp,null,RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),Paint(3))
    doc.finishPage(page)
   }
   val name="Pdf_${System.currentTimeMillis()}.pdf"
   val values=ContentValues().apply{
    put(MediaStore.Downloads.DISPLAY_NAME,name)
    put(MediaStore.Downloads.MIME_TYPE,"application/pdf")
    put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Pdf")
   }
   val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:error("Kayıt konumu açılamadı")
   contentResolver.openOutputStream(uri)?.use{doc.writeTo(it)}?:error("Dosya yazılamadı")
   message("PDF kaydedildi: Downloads/Pdf")
  }catch(e:Exception){message("PDF kaydedilemedi: ${e.message}")}finally{doc.close()}
 }
}
