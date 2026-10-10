package com.deckos.keyboard

import android.inputmethodservice.InputMethodService
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.util.Locale

class DeckKeyboardService : InputMethodService() {
 private var shift=false
 private var caps=false
 private var ctrl=false
 private var alt=false
 private var panel="ABC"
 private var lastShift=0L
 private var lastSpace=0L
 private var repeatKey:String?=null
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var root:LinearLayout
 private lateinit var suggestions:LinearLayout
 private val bg=Color.rgb(21,21,27)
 private val keyBg=Color.rgb(49,47,57)
 private val accent=Color.rgb(185,99,155)
 private val words=listOf("the","to","and","a","of","you","it","is","in","that","for","on","with","this","have","be","are","was","not","but","we","they","can","will","would","should","could","hello","help","how","what","when","where","why","who","there","their","then","than","your","you're","yes","please","thanks","thank","good","great","morning","night","today","tomorrow","later","time","typing","type","text","keyboard","phone","message","people","really","right","wrong","about","after","before","because","between","something","someone","somewhere","anything","everything","nothing","going","doing","being","having","working","work","make","made","want","wanted","need","needed","like","liked","love","look","looking","think","thinking","know","knowing","don't","can't","won't","it's","i'm","i'll","i've","we're","they're","that's","there's","does","did","done","more","most","much","many","some","just","very","little","big","small","first","last","next","new","old","now","here","there","okay","ok","sure","fine","nice","better","best","again","still","also","only","other","another","every","each","all","any","my","me","our","us","he","she","his","her","them","him","from","into","over","under","up","down","left","right","back","space","word","words","sentence","sentences","automatic","capital","capitalisation","prediction","predictive","suggestion","suggestions","correct","correction","autocorrect","android","pixel","deck","terminal","code","project","build","version","test","testing","problem","problems","feature","features","change","changes","update","updates","github","vercel")
 private val commonTypos=mapOf("teh" to "the","adn" to "and","thier" to "their","recieve" to "receive","dont" to "don't","cant" to "can't","im" to "I'm","ive" to "I've","ill" to "I'll","youre" to "you're","woudl" to "would","becuase" to "because")
 private val prefs get()=getSharedPreferences("keyboard",MODE_PRIVATE)
 private val privateField get():Boolean {
  val type=currentInputEditorInfo?.inputType ?: 0
  val variation=type and InputType.TYPE_MASK_VARIATION
  return variation==InputType.TYPE_TEXT_VARIATION_PASSWORD || variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD
 }
 private val terminalField get():Boolean {
  val info=currentInputEditorInfo ?: return false
  return info.inputType==InputType.TYPE_NULL || (info.packageName?.contains("termux",true)==true)
 }
 private val predictionsEnabled get()=!privateField && !terminalField && prefs.getBoolean("suggestions",true)
 override fun onStartInput(attribute:EditorInfo?, restarting:Boolean) {
  super.onStartInput(attribute,restarting)
  shift=false; caps=false; ctrl=false; alt=false
 }
 override fun onCreateInputView():View {
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(2,4,2,0) }
  drawKeys()
  return root
 }
 private val Int.dp get()=(this*resources.displayMetrics.density).toInt()
 private fun row(keys:List<String>,weights:List<Float>?=null) {
  val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
  val height=if(landscape) 42 else when(prefs.getString("height","standard")) {"compact"->47;"tall"->65;else->56}
  keys.forEachIndexed { index,key ->
   val button=Button(this).apply {
    text=when(key) {"Space"->"English";"Shift"->if(caps)"⇪" else "⇧";else->key}
    textSize=if(key.length>4) 11f else 17f
    setAllCaps(false)
    setTextColor(Color.WHITE)
    setBackgroundTintList(android.content.res.ColorStateList.valueOf(if((key=="Ctrl"&&ctrl)||(key=="Alt"&&alt)||(key=="Shift"&&(shift||caps))||key==panel) accent else keyBg))
    minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
    setPadding(0,0,0,0)
    setOnClickListener { if(key!="⌫" && key!="Space") press(key) }
    if(key=="⌫" || key=="Space") {
     setOnTouchListener(object:View.OnTouchListener {
      var startX=0f
      var moved=0
      var holding=false
      val hold=Runnable { holding=true; if(key=="⌫") { erase(); repeatKey="⌫"; handler.postDelayed(repeater,65) } }
      override fun onTouch(v:View,event:android.view.MotionEvent):Boolean {
       when(event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { startX=event.x; moved=0; holding=false; handler.postDelayed(hold,380); return true }
        MotionEvent.ACTION_MOVE -> {
         if(key=="Space" && holding) {
          val delta=((event.x-startX)/18.dp).toInt()
          val diff=delta-moved
          if(diff!=0) { repeat(kotlin.math.abs(diff)) { sendCode(if(diff>0)KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) }; moved=delta }
         }
         return true
        }
        MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
         handler.removeCallbacks(hold); handler.removeCallbacks(repeater); repeatKey=null
         if(event.actionMasked==MotionEvent.ACTION_UP && !holding) press(key)
         return true
        }
       }
       return true
      }
     })
    }
   }
   line.addView(button,LinearLayout.LayoutParams(0,height.dp,weights?.get(index) ?: 1f).apply { setMargins(1.dp,1.dp,1.dp,1.dp) })
  }
  root.addView(line)
 }
 private val repeater=object:Runnable { override fun run() { if(repeatKey=="⌫") { erase(); handler.postDelayed(this,65) } } }
 private fun drawKeys() {
  if(!::root.isInitialized)return
  root.removeAllViews()
  suggestions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  root.addView(suggestions,LinearLayout.LayoutParams(-1,42.dp))
  updateSuggestions()
  row(listOf("ABC","SYM","NAV","FN","NUM"))
  if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) row((1..12).map{"F$it"})
  when(panel) {
   "ABC" -> {
    row("qwertyuiop".map{it.toString()})
    row("asdfghjkl".map{it.toString()})
    row(listOf("Shift")+ "zxcvbnm".map{it.toString()} + "⌫")
    row(listOf("123",",","Ctrl","Space",".","Enter"),listOf(1f,0.8f,1.2f,4.4f,0.8f,1.2f))
   }
   "SYM" -> { row(listOf("1","2","3","4","5","6","7","8","9","0"));row(listOf("!","@","#","$","%","^","&","*","(",")"));row(listOf("-","_","=","+","[","]","{","}","⌫"));row(listOf("ABC","/","\\","Space","?","Enter"),listOf(1f,1f,1f,4f,1f,1f)) }
   "NAV" -> { row(listOf("Home","↑","End","PgUp"));row(listOf("←","↓","→","PgDn"));row(listOf("Ins","Del","Tab","Esc"));row(listOf("ABC","Ctrl","Alt","Space","Enter"),listOf(1f,1f,1f,4f,1f)) }
   "FN" -> { row(listOf("F1","F2","F3","F4"));row(listOf("F5","F6","F7","F8"));row(listOf("F9","F10","F11","F12"));row(listOf("ABC","Ctrl","Alt","Space","Esc"),listOf(1f,1f,1f,4f,1f)) }
   "NUM" -> { row(listOf("7","8","9","/"));row(listOf("4","5","6","*"));row(listOf("1","2","3","-"));row(listOf("ABC","0",".","+","Enter")) }
  }
  val clearance=prefs.getInt("clearance",12)
  root.addView(View(this),LinearLayout.LayoutParams(-1,clearance.dp))
 }
 private fun beforeCursor()=currentInputConnection?.getTextBeforeCursor(100,0)?.toString().orEmpty()
 private fun currentWord()=Regex("[A-Za-z']+$").find(beforeCursor())?.value.orEmpty()
 private fun updateSuggestions() {
  if(!::suggestions.isInitialized)return
  suggestions.removeAllViews()
  val current=currentWord().lowercase(Locale.ENGLISH)
  val candidates=if(predictionsEnabled && panel=="ABC" && current.isNotEmpty()) {
   (listOfNotNull(commonTypos[current])+words.filter{it.startsWith(current) && it!=current}).distinct().take(3)
  } else emptyList()
  for(i in 0..2) {
   val candidate=candidates.getOrNull(i).orEmpty()
   val item=TextView(this).apply {
    text=candidate; gravity=Gravity.CENTER; textSize=16f; setTextColor(Color.WHITE)
    setOnClickListener { if(candidate.isNotEmpty()) replaceWord(candidate) }
   }
   suggestions.addView(item,LinearLayout.LayoutParams(0,-1,1f))
  }
 }
 private fun replaceWord(word:String) {
  val ic=currentInputConnection ?:return
  val old=currentWord()
  ic.beginBatchEdit()
  ic.deleteSurroundingText(old.length,0)
  ic.commitText(if(old.firstOrNull()?.isUpperCase()==true) word.replaceFirstChar{it.uppercase()} else word,1)
  ic.endBatchEdit()
  updateSuggestions()
 }
 private fun erase() {
  val ic=currentInputConnection ?:return
  if(ic.getSelectedText(0)!=null) ic.commitText("",1) else ic.deleteSurroundingText(1,0)
  updateSuggestions()
 }
 private fun press(key:String) {
  if(prefs.getBoolean("haptics",false)) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  when(key) {
   "ABC","SYM","NAV","FN","NUM" -> { panel=key;drawKeys();return }
   "123" -> { panel="SYM";drawKeys();return }
   "Ctrl" -> { ctrl=!ctrl;drawKeys();return }
   "Alt" -> { alt=!alt;drawKeys();return }
   "Shift" -> {
    val now=android.os.SystemClock.uptimeMillis()
    if(now-lastShift<350) {caps=!caps;shift=caps} else shift=!shift
    lastShift=now;drawKeys();return
   }
  }
  val ic=currentInputConnection ?:return
  val code=when(key) {
   "⌫"->KeyEvent.KEYCODE_DEL;"Enter"->KeyEvent.KEYCODE_ENTER;"Tab"->KeyEvent.KEYCODE_TAB;"Esc"->KeyEvent.KEYCODE_ESCAPE
   "↑"->KeyEvent.KEYCODE_DPAD_UP;"↓"->KeyEvent.KEYCODE_DPAD_DOWN;"←"->KeyEvent.KEYCODE_DPAD_LEFT;"→"->KeyEvent.KEYCODE_DPAD_RIGHT
   "Home"->KeyEvent.KEYCODE_MOVE_HOME;"End"->KeyEvent.KEYCODE_MOVE_END;"PgUp"->KeyEvent.KEYCODE_PAGE_UP;"PgDn"->KeyEvent.KEYCODE_PAGE_DOWN
   "Ins"->KeyEvent.KEYCODE_INSERT;"Del"->KeyEvent.KEYCODE_FORWARD_DEL
   else->if(key.startsWith("F") && key.drop(1).toIntOrNull() in 1..12) KeyEvent.KEYCODE_F1+key.drop(1).toInt()-1 else null
  }
  val meta=(if(ctrl)KeyEvent.META_CTRL_ON else 0) or (if(alt)KeyEvent.META_ALT_ON else 0) or (if(shift||caps)KeyEvent.META_SHIFT_ON else 0)
  if(code!=null) sendCode(code,meta)
  else if(key=="Space") {
   val before=beforeCursor()
   val old=currentWord()
   if(predictionsEnabled && prefs.getBoolean("autocorrect",true) && commonTypos.containsKey(old.lowercase(Locale.ENGLISH))) replaceWord(commonTypos.getValue(old.lowercase(Locale.ENGLISH)))
   val now=android.os.SystemClock.uptimeMillis()
   if(prefs.getBoolean("double_space",true) && now-lastSpace<350 && beforeCursor().endsWith(" ")) {
    ic.deleteSurroundingText(1,0);ic.commitText(". ",1)
   } else ic.commitText(" ",1)
   lastSpace=now
  } else if(key.length==1) {
   val before=beforeCursor()
   val autoCap=before.trimEnd().isEmpty() || Regex("[.!?]\\s+$").containsMatchIn(before)
   val char=if(shift||caps||(prefs.getBoolean("autocap",true)&&autoCap)) key.uppercase(Locale.ENGLISH) else key
   if(ctrl||alt) {
    val k=KeyEvent.keyCodeFromString("KEYCODE_"+key.uppercase(Locale.ENGLISH))
    if(k!=KeyEvent.KEYCODE_UNKNOWN) sendCode(k,meta) else ic.commitText(char,1)
   } else ic.commitText(char,1)
   lastSpace=0L
  }
  shift=false;ctrl=false;alt=false
  drawKeys()
 }
 private fun sendCode(code:Int,meta:Int=0) {
  val ic=currentInputConnection?:return
  val t=android.os.SystemClock.uptimeMillis()
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0,meta))
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_UP,code,0,meta))
 }
}
