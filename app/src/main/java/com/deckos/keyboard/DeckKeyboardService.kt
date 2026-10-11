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
 private var lastTypedAt=0L
 private var lastSpace=0L
 private var lastCorrectionOriginal:String?=null
 private var lastCorrectionResult:String?=null
 private var repeatKey:String?=null
 private val accents=mapOf(
  "a" to listOf("á","à","â","ä","ã","å","æ"),
  "e" to listOf("é","è","ê","ë","ē"),
  "i" to listOf("í","ì","î","ï","ī"),
  "o" to listOf("ó","ò","ô","ö","õ","ø"),
  "u" to listOf("ú","ù","û","ü","ū"),
  "y" to listOf("ý","ÿ"),
  "c" to listOf("ç"),"n" to listOf("ñ"),"s" to listOf("ß","š"),
  "z" to listOf("ž"),"d" to listOf("ð"),"t" to listOf("þ")
 )
 private var popup:PopupWindow?=null
 private var popupLeft=0
 private var popupCellWidth=0
 private val letterButtons=mutableListOf<Pair<String,Button>>()
 private var shiftButton:Button?=null
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var root:LinearLayout
 private lateinit var suggestions:LinearLayout
 private val bg=Color.rgb(21,21,27)
 private val keyBg=Color.rgb(49,47,57)
 private val accent=Color.rgb(185,99,155)
 private val words=listOf("the","to","and","a","of","you","it","is","in","that","for","on","with","this","have","be","are","was","not","but","we","they","can","will","would","should","could","hello","help","how","what","when","where","why","who","there","their","then","than","your","you're","yes","please","thanks","thank","good","great","morning","night","today","tomorrow","later","time","typing","type","text","keyboard","phone","message","people","really","right","wrong","about","after","before","because","between","something","someone","somewhere","anything","everything","nothing","going","doing","being","having","working","work","make","made","want","wanted","need","needed","like","liked","love","look","looking","think","thinking","know","knowing","don't","can't","won't","it's","i'm","i'll","i've","we're","they're","that's","there's","does","did","done","more","most","much","many","some","just","very","little","big","small","first","last","next","new","old","now","here","there","okay","ok","sure","fine","nice","better","best","again","still","also","only","other","another","every","each","all","any","my","me","our","us","he","she","his","her","them","him","from","into","over","under","up","down","left","right","back","space","word","words","sentence","sentences","automatic","capital","capitalisation","prediction","predictive","suggestion","suggestions","correct","correction","autocorrect","android","pixel","deck","terminal","code","project","build","version","test","testing","problem","problems","feature","features","change","changes","update","updates","github","vercel")
 private val dictionary:List<String> by lazy {
  assets.open("english_frequency.txt").bufferedReader().use { it.readLines() }
 }
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
  shift=false; caps=false; ctrl=false; alt=false; panel="ABC"
  if(::root.isInitialized) drawKeys()
 }
 private fun autoCapital():Boolean {
  val before=beforeCursor()
  return before.trimEnd().isEmpty() || Regex("[.!?]\\s+$").containsMatchIn(before)
 }
 private fun uppercaseActive()=caps || shift || (panel=="ABC" && prefs.getBoolean("autocap",true) && autoCapital())
 override fun onCreateInputView():View {
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(2,4,2,0) }
  root.setOnApplyWindowInsetsListener { _,insets ->
   val bottom=insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
   val safe=bottom.coerceAtLeast(40.dp)
   if(root.paddingBottom!=safe) root.setPadding(root.paddingLeft,root.paddingTop,root.paddingRight,safe)
   insets
  }
  root.setPadding(root.paddingLeft,root.paddingTop,root.paddingRight,40.dp)
  drawKeys()
  return root
 }
 private val Int.dp get()=(this*resources.displayMetrics.density).toInt()
 private fun row(keys:List<String>,weights:List<Float>?=null) {
  val line=object:LinearLayout(this) {
   private var active:View?=null
   override fun dispatchTouchEvent(event:MotionEvent):Boolean {
    if(childCount==0)return super.dispatchTouchEvent(event)
    if(event.actionMasked==MotionEvent.ACTION_DOWN) {
     active=(0 until childCount).map{getChildAt(it)}.firstOrNull { event.x >= it.left && event.x < it.right }
      ?: (0 until childCount).map{getChildAt(it)}.minByOrNull {
       when { event.x < it.left -> it.left-event.x; event.x > it.right -> event.x-it.right; else -> 0f }
      }
    }
    val target=active
    if(target!=null) {
     val shifted=MotionEvent.obtain(event)
     shifted.offsetLocation(-target.left.toFloat(),-target.top.toFloat())
     val handled=target.dispatchTouchEvent(shifted)
     shifted.recycle()
     if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) {
      active=null
     }
     return handled
    }
    return super.dispatchTouchEvent(event)
   }
  }.apply { orientation=LinearLayout.HORIZONTAL }
  val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
  val height=if(keys==listOf("ABC","SYM","NAV","FN","NUM")) 36 else if(landscape) 42 else when(prefs.getString("height","standard")) {"compact"->47;"tall"->65;else->56}
  keys.forEachIndexed { index,key ->
   val button=Button(this).apply {
    text=when(key) {
     "Space"->"English"
     "Shift"->if(caps)"⇪" else "⇧"
     else->if(panel=="ABC" && key.length==1 && key[0] in 'a'..'z' && uppercaseActive()) key.uppercase(Locale.ENGLISH) else key
    }
    textSize=if(key.length>4) 11f else 17f
    setAllCaps(false)
    setTextColor(Color.WHITE)
    setBackgroundTintList(android.content.res.ColorStateList.valueOf(if((key=="Ctrl"&&ctrl)||(key=="Alt"&&alt)||(key=="Shift"&&uppercaseActive())||key==panel) accent else keyBg))
    minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
    setPadding(0,0,0,0)
    setOnClickListener { if(key!="⌫" && key!="Space") press(key) }
    val extras=when {
     panel=="ABC" && key.length==1 && "qwertyuiop".contains(key) ->
      listOf(("qwertyuiop".indexOf(key)+1)%10).map{if(it==0)"0" else it.toString()} + accents[key].orEmpty()
     key=="." -> listOf("'","!","?","…")
     panel=="ABC" -> accents[key].orEmpty()
     else -> emptyList()
    }
    if(extras.isNotEmpty() && key!="Space" && key!="⌫") {
     setOnTouchListener(object:View.OnTouchListener {
      var opened=false
      var chosen=0
      val hold=Runnable { opened=true; chosen=0; showChoices(this@DeckKeyboardService,button=this@apply,options=extras) }
      override fun onTouch(v:View,event:MotionEvent):Boolean {
       when(event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { opened=false;chosen=0;handler.postDelayed(hold,370);v.isPressed=true;return true }
        MotionEvent.ACTION_MOVE -> {
         if(opened) {chosen=((event.rawX-popupLeft)/popupCellWidth.coerceAtLeast(1)).toInt().coerceIn(0,extras.lastIndex);updateChoice(extras,chosen)}
         return true
        }
        MotionEvent.ACTION_UP -> {
         handler.removeCallbacks(hold);v.isPressed=false
         if(opened) {updateSuggestions();press(extras[chosen])} else press(key)
         return true
        }
        MotionEvent.ACTION_CANCEL -> {handler.removeCallbacks(hold);if(opened)updateSuggestions();v.isPressed=false;return true}
       }
       return true
      }
     })
    }
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
          val delta=((event.x-startX)/9.dp).toInt()
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
   if(panel=="ABC" && key.length==1 && key[0] in 'a'..'z') letterButtons.add(key to button)
   if(panel=="ABC" && key=="Shift") shiftButton=button
   line.addView(button,LinearLayout.LayoutParams(0,height.dp,weights?.get(index) ?: 1f).apply { setMargins(1.dp,1.dp,1.dp,1.dp) })
  }
  root.addView(line)
 }
 private fun showChoices(service:DeckKeyboardService,button:View,options:List<String>) {
  // Render inside the IME window: external popups above the keyboard can be clipped by Android.
  if(!::suggestions.isInitialized)return
  suggestions.removeAllViews()
  val location=IntArray(2)
  suggestions.getLocationOnScreen(location)
  popupLeft=location[0]
  popupCellWidth=(suggestions.width/options.size).coerceAtLeast(1)
  options.forEachIndexed { i,value ->
   suggestions.addView(TextView(service).apply {
    text=value
    gravity=Gravity.CENTER
    textSize=20f
    setTextColor(Color.WHITE)
    setBackgroundColor(if(i==0)accent else keyBg)
   },LinearLayout.LayoutParams(0,-1,1f))
  }
 }
 private fun updateChoice(options:List<String>,index:Int) {
  if(!::suggestions.isInitialized)return
  for(i in 0 until suggestions.childCount) {
   suggestions.getChildAt(i).setBackgroundColor(if(i==index)accent else keyBg)
  }
 }
 private val repeater=object:Runnable {
  override fun run() {
   if(repeatKey=="⌫") {
    erase()
    handler.postDelayed(this,65)
   }
  }
 }
 private fun drawKeys() {
  if(!::root.isInitialized)return
  root.removeAllViews()
  letterButtons.clear();shiftButton=null
  suggestions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  root.addView(suggestions,LinearLayout.LayoutParams(-1,42.dp))
  updateSuggestions()
  row(listOf("ABC","SYM","NAV","FN","NUM"))
  if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) row((1..12).map{"F$it"})
  when(panel) {
   "ABC" -> {
    row("qwertyuiop".map{it.toString()})
    row("asdfghjkl".map{it.toString()})
    row(listOf("Shift")+ "zxcvbnm".map{it.toString()} + "⌫",listOf(1.6f)+List(7){1f}+listOf(1.6f))
    row(listOf("Ctrl",",","Space",".","Enter"),listOf(1.2f,0.8f,4.4f,0.8f,1.2f))
   }
   "SYM" -> {
    row(listOf("!","?","@", "#","$","%","^","&","*","~"))
    row(listOf(":", ";","(",")","[","]","{","}","<",">"))
    row(listOf("-","_","=","+","/","\\","|","`","\"","⌫"))
    row(listOf("ABC",",","'","Space",".","Enter"),listOf(1f,1f,1f,4f,1f,1f))
   }
   "NAV" -> { row(listOf("Home","↑","End","PgUp"));row(listOf("←","↓","→","PgDn"));row(listOf("Ins","Del","Tab","Esc"));row(listOf("ABC","Ctrl","Alt","Space","Enter"),listOf(1f,1f,1f,4f,1f)) }
   "FN" -> { row(listOf("F1","F2","F3","F4"));row(listOf("F5","F6","F7","F8"));row(listOf("F9","F10","F11","F12"));row(listOf("ABC","Ctrl","Alt","Space","Esc"),listOf(1f,1f,1f,4f,1f)) }
   "NUM" -> { row(listOf("7","8","9","/"));row(listOf("4","5","6","*"));row(listOf("1","2","3","-"));row(listOf("ABC","0",".","+","Enter")) }
  }
  // The bottom inset above reserves space for Android's keyboard switcher.
  // User clearance adds only optional extra breathing room.
  val clearance=0
  root.addView(View(this),LinearLayout.LayoutParams(-1,clearance.dp))
 }
 private fun beforeCursor()=currentInputConnection?.getTextBeforeCursor(100,0)?.toString().orEmpty()
 private fun currentWord()=Regex("[A-Za-z']+$").find(beforeCursor())?.value.orEmpty()
 private fun updateSuggestions() {
  if(!::suggestions.isInitialized)return
  suggestions.removeAllViews()
  val current=currentWord().lowercase(Locale.ENGLISH)
  val candidates=if(predictionsEnabled && panel=="ABC" && current.isNotEmpty()) {
   (listOfNotNull(commonTypos[current]) + dictionary.asSequence().filter{it.startsWith(current) && it!=current}.take(6).toList() +
    words.filter{it.startsWith(current) && it!=current}).distinct().take(3)
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
 private val keyboardRows=listOf("qwertyuiop","asdfghjkl","zxcvbnm")
 private fun nearby(a:Char,b:Char):Boolean {
  var ax=-1;var ay=-1;var bx=-1;var by=-1
  keyboardRows.forEachIndexed { row,letters ->
   val i=letters.indexOf(a);if(i>=0){ax=i;ay=row}
   val j=letters.indexOf(b);if(j>=0){bx=j;by=row}
  }
  if(ax<0 || bx<0)return false
  val offsets=listOf(0f,0.5f,1.5f)
  return kotlin.math.abs((ax+offsets[ay])-(bx+offsets[by]))<=1.15f && kotlin.math.abs(ay-by)<=1
 }
 // Weighted edit distance: neighbouring keys and adjacent swaps cost less.
 // Two mistakes are allowed, but confidence thresholds remain conservative.
 private fun typoCost(typed:String,target:String):Int {
  if(kotlin.math.abs(typed.length-target.length)>2)return 99
  val n=typed.length;val m=target.length
  val d=Array(n+1){IntArray(m+1)}
  for(i in 0..n)d[i][0]=i*2
  for(j in 0..m)d[0][j]=j*2
  for(i in 1..n)for(j in 1..m) {
   val sub=if(typed[i-1]==target[j-1])0 else if(nearby(typed[i-1],target[j-1]))1 else 3
   var cost=minOf(d[i-1][j]+2,d[i][j-1]+2,d[i-1][j-1]+sub)
   if(i>1 && j>1 && typed[i-1]==target[j-2] && typed[i-2]==target[j-1])
    cost=minOf(cost,d[i-2][j-2]+1)
   d[i][j]=cost
  }
  return d[n][m]
 }
 private fun correctionFor(original:String):String? {
  val typed=original.lowercase(Locale.ENGLISH)
  if(typed.isEmpty())return null
  val explicit=when(typed) {
   "i"->"I";"im"->"I'm";"ive"->"I've";"ill"->"I'll"
   else->commonTypos[typed]
  }
  if(explicit!=null)return explicit
  val strength=prefs.getString("correction_strength","moderate") ?: "moderate"
  if(strength=="off" || typed.length<3 || typed.length>18 || !typed.all{it in 'a'..'z'})return null
  // Never rewrite an accepted dictionary word solely on spelling similarity.
  if(typed in dictionary || typed in words)return null
  val limit=when(strength) {"gentle"->2;"strong"->5;else->4}
  var best:String?=null
  var bestScore=Int.MAX_VALUE
  // The dictionary is ordered by frequency. Rank by typo plausibility first,
  // then frequency; no sorting or view rebuilding on the typing hot path.
  dictionary.forEachIndexed { index,word ->
   if(word.length in (typed.length-2)..(typed.length+2) && word.all{it in 'a'..'z'}) {
    val cost=typoCost(typed,word)
    if(cost<=limit) {
     val score=cost*100+index/150
     if(score<bestScore) {bestScore=score;best=word}
    }
   }
  }
  return if(bestScore<=limit*100+30)best else null
 }
 private fun replaceWord(word:String) {
  val ic=currentInputConnection ?:return
  val old=currentWord()
  ic.beginBatchEdit()
  ic.deleteSurroundingText(old.length,0)
  val replacement=if(old.firstOrNull()?.isUpperCase()==true || word=="i" || word=="i'm" || word=="i'll" || word=="i've") word.replaceFirstChar{it.uppercase()} else word
  ic.commitText(replacement+" ",1)
  lastCorrectionOriginal=null;lastCorrectionResult=null
  ic.endBatchEdit()
  updateSuggestions()
 }
 private fun refreshTypingUi() {
  if(!::root.isInitialized || !::suggestions.isInitialized) return
  if(panel=="ABC") {
   val upper=uppercaseActive()
   letterButtons.forEach { (letter,button) -> button.text=if(upper)letter.uppercase(Locale.ENGLISH) else letter }
   shiftButton?.backgroundTintList=android.content.res.ColorStateList.valueOf(if(upper)accent else keyBg)
  }
  updateSuggestions()
 }
 private fun erase() {
  val ic=currentInputConnection ?:return
  val before=beforeCursor()
  val result=lastCorrectionResult
  val original=lastCorrectionOriginal
  if(result!=null && original!=null && before.endsWith(result+" ")) {
   ic.deleteSurroundingText(result.length+1,0)
   ic.commitText(original,1)
  } else if(ic.getSelectedText(0)!=null) ic.commitText("",1) else ic.deleteSurroundingText(1,0)
  lastCorrectionOriginal=null;lastCorrectionResult=null
  updateSuggestions()
 }
 private fun correctCurrentWord() {
  val ic=currentInputConnection ?:return
  val old=currentWord()
  val correction=if(!privateField && !terminalField && prefs.getBoolean("autocorrect",true))
   correctionFor(old) else null
  if(correction!=null && old.isNotEmpty()) {
   val corrected=if(old.first().isUpperCase()) correction.replaceFirstChar{it.uppercase()} else correction
   if(corrected!=old) {
    ic.deleteSurroundingText(old.length,0)
    ic.commitText(corrected,1)
    lastCorrectionOriginal=old
    lastCorrectionResult=corrected
    return
   }
  }
  lastCorrectionOriginal=null;lastCorrectionResult=null
 }
 private fun press(key:String) {
  if(prefs.getBoolean("haptics",false)) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  when(key) {
   "ABC","SYM","NAV","FN","NUM" -> {
    val elapsed=android.os.SystemClock.uptimeMillis()-lastTypedAt
    if(elapsed<220L) {
     handler.postDelayed({ if(::root.isInitialized) {panel=key;drawKeys()} },220L-elapsed)
    } else {panel=key;root.post {drawKeys()} }
    return
   }
   "123" -> { panel="SYM";root.post { drawKeys() };return }
   "Ctrl" -> { ctrl=!ctrl;root.post { drawKeys() };return }
   "Alt" -> { alt=!alt;root.post { drawKeys() };return }
   "Shift" -> {
    val now=android.os.SystemClock.uptimeMillis()
    if(now-lastShift<350) {caps=!caps;shift=caps} else shift=!shift
    lastShift=now;root.post { drawKeys() };return
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
   correctCurrentWord()
   val now=android.os.SystemClock.uptimeMillis()
   if(prefs.getBoolean("double_space",true) && now-lastSpace<350 && beforeCursor().endsWith(" ")) {
    ic.deleteSurroundingText(1,0);ic.commitText(". ",1)
   } else ic.commitText(" ",1)
   lastSpace=now
  } else if(key.length==1 || key=="…") {
   if(key in listOf(".",",","!","?","…") && beforeCursor().endsWith(" ")) ic.deleteSurroundingText(1,0)
   if(key in listOf(".","!","?"))correctCurrentWord()
   val before=beforeCursor()
   val autoCap=autoCapital()
   val char=if(shift||caps||(prefs.getBoolean("autocap",true)&&autoCap)) key.uppercase(Locale.ENGLISH) else key
   if(ctrl||alt) {
    val k=KeyEvent.keyCodeFromString("KEYCODE_"+key.uppercase(Locale.ENGLISH))
    if(k!=KeyEvent.KEYCODE_UNKNOWN) sendCode(k,meta) else ic.commitText(char,1)
   } else ic.commitText(char,1)
   lastSpace=0L
   if(key !in listOf(".","!","?")) {lastCorrectionOriginal=null;lastCorrectionResult=null}
  }
  if(key.length==1 || key=="Space" || key=="⌫" || key=="…")lastTypedAt=android.os.SystemClock.uptimeMillis()
  shift=false;ctrl=false;alt=false
  if(key in listOf("ABC","SYM","NAV","FN","NUM","Shift","Ctrl","Alt")) {
   root.post { drawKeys() }
  } else refreshTypingUi()
 }
 private fun sendCode(code:Int,meta:Int=0) {
  val ic=currentInputConnection?:return
  val t=android.os.SystemClock.uptimeMillis()
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0,meta))
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_UP,code,0,meta))
 }
}
