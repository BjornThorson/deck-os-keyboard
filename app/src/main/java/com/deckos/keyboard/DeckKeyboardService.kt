package com.deckos.keyboard

import android.inputmethodservice.InputMethodService
import android.content.res.Configuration
import android.graphics.Color
import android.view.*
import android.view.inputmethod.InputConnection
import android.widget.*

class DeckKeyboardService : InputMethodService() {
 private var shift=false
 private var ctrl=false
 private var alt=false
 private var panel="ABC"
 private lateinit var root: LinearLayout
 private val background=Color.rgb(21,21,27)
 private val keyBackground=Color.rgb(48,45,56)
 private val accent=Color.rgb(201,112,171)

 override fun onCreateInputView(): View {
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(background); setPadding(4,6,4,6) }
  drawKeys()
  return root
 }
 private fun row(vararg keys: String) {
  val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  for (key in keys) {
   val b=Button(this).apply {
    text=key
    textSize=if(key.length>4) 10f else 14f
    setAllCaps(false)
    setTextColor(Color.WHITE)
    setBackgroundTintList(android.content.res.ColorStateList.valueOf(if ((key=="Ctrl"&&ctrl)||(key=="Alt"&&alt)||(key=="Shift"&&shift)||key==panel) accent else keyBackground))
    minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
    setPadding(0,0,0,0)
    setOnClickListener { press(key) }
   }
   line.addView(b,LinearLayout.LayoutParams(0,if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 39.dp else 48.dp, if(key=="Space") 3f else 1f).apply { setMargins(2,2,2,2) })
  }
  root.addView(line)
 }
 private val Int.dp get()=(this*resources.displayMetrics.density).toInt()
 private fun drawKeys() {
  root.removeAllViews()
  row("ABC","SYM","NAV","FN","NUM")
  val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
  if (landscape) row("F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12")
  when(panel) {
   "ABC" -> {
    row("1","2","3","4","5","6","7","8","9","0")
    row("q","w","e","r","t","y","u","i","o","p")
    row("a","s","d","f","g","h","j","k","l")
    row("Shift","z","x","c","v","b","n","m","⌫")
   }
   "SYM" -> { row("!","@","#","$","%","^","&","*","(",")"); row("-","_","=","+","[","]","{","}"); row("/","\\","|",":",";","'","\"","?") }
   "NAV" -> { row("Home","↑","End","PgUp"); row("←","↓","→","PgDn"); row("Ins","Del","⌫","Tab") }
   "FN" -> { row("F1","F2","F3","F4"); row("F5","F6","F7","F8"); row("F9","F10","F11","F12") }
   "NUM" -> { row("7","8","9","/"); row("4","5","6","*"); row("1","2","3","-"); row("0",".","Enter","+") }
  }
  row("Ctrl","Alt","Esc","Tab","Space","Enter","⌫")
 }
 private fun press(key:String) {
  if(getSharedPreferences("keyboard",MODE_PRIVATE).getBoolean("haptics",false)) root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
  when(key) {
   "ABC","SYM","NAV","FN","NUM" -> { panel=key; drawKeys(); return }
   "Ctrl" -> { ctrl=!ctrl; drawKeys(); return }
   "Alt" -> { alt=!alt; drawKeys(); return }
   "Shift" -> { shift=!shift; drawKeys(); return }
  }
  val ic=currentInputConnection ?: return
  val code=when(key) {
   "⌫" -> KeyEvent.KEYCODE_DEL
   "Enter" -> KeyEvent.KEYCODE_ENTER
   "Tab" -> KeyEvent.KEYCODE_TAB
   "Esc" -> KeyEvent.KEYCODE_ESCAPE
   "↑" -> KeyEvent.KEYCODE_DPAD_UP
   "↓" -> KeyEvent.KEYCODE_DPAD_DOWN
   "←" -> KeyEvent.KEYCODE_DPAD_LEFT
   "→" -> KeyEvent.KEYCODE_DPAD_RIGHT
   "Home" -> KeyEvent.KEYCODE_MOVE_HOME
   "End" -> KeyEvent.KEYCODE_MOVE_END
   "PgUp" -> KeyEvent.KEYCODE_PAGE_UP
   "PgDn" -> KeyEvent.KEYCODE_PAGE_DOWN
   "Ins" -> KeyEvent.KEYCODE_INSERT
   "Del" -> KeyEvent.KEYCODE_FORWARD_DEL
   else -> if(key.startsWith("F") && key.drop(1).toIntOrNull() in 1..12) KeyEvent.KEYCODE_F1+(key.drop(1).toInt()-1) else null
  }
  val meta=(if(ctrl) KeyEvent.META_CTRL_ON else 0) or (if(alt) KeyEvent.META_ALT_ON else 0) or (if(shift) KeyEvent.META_SHIFT_ON else 0)
  if(code!=null) sendKey(ic,code,meta)
  else if(key=="Space") ic.commitText(" ",1)
  else if(key.length==1) {
   val char=if(shift) key.uppercase() else key
   if(ctrl || alt) {
    val k=KeyEvent.keyCodeFromString("KEYCODE_"+key.uppercase())
    if(k!=KeyEvent.KEYCODE_UNKNOWN) sendKey(ic,k,meta) else ic.commitText(char,1)
   } else ic.commitText(char,1)
  }
  shift=false; ctrl=false; alt=false
  drawKeys()
 }
 private fun sendKey(ic:InputConnection, code:Int, meta:Int) {
  val t=android.os.SystemClock.uptimeMillis()
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0,meta))
  ic.sendKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_UP,code,0,meta))
 }
}
