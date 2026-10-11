package com.deckos.keyboard

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.*

class SettingsActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("keyboard", MODE_PRIVATE)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 24); setBackgroundColor(0xff15151b.toInt()) }
  fun label(s: String) = TextView(this).apply { text=s; textSize=19f; setTextColor(0xffeeeeee.toInt()); setPadding(0, 12, 0, 12) }
  root.addView(label("DECK/OS KEYBOARD  //  SETTINGS"))
  val haptics = Switch(this).apply { text="Keypress vibration"; isChecked=prefs.getBoolean("haptics",false); setTextColor(-1); setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("haptics",on).apply() } }
  root.addView(haptics)
  fun toggle(name:String,key:String,default:Boolean) {
   root.addView(Switch(this).apply { text=name; isChecked=prefs.getBoolean(key,default); setTextColor(-1); setOnCheckedChangeListener { _,on -> prefs.edit().putBoolean(key,on).apply() } })
  }
  toggle("Word suggestions","suggestions",true)
  toggle("Automatic spelling correction","autocorrect",true)
  toggle("Automatic sentence capitals","autocap",true)
  toggle("Double space for period","double_space",true)
  root.addView(label("Autocorrect strength"))
  val strengths=listOf("off","gentle","moderate","strong")
  val correctionStrength=Spinner(this)
  correctionStrength.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,strengths)
  correctionStrength.setSelection(strengths.indexOf(prefs.getString("correction_strength","moderate")).coerceAtLeast(0))
  correctionStrength.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
    prefs.edit().putString("correction_strength",strengths[position]).apply()
   }
  }
  root.addView(correctionStrength)
  root.addView(label("Keyboard height"))
  val heights=Spinner(this)
  heights.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("compact","standard","tall"))
  heights.setSelection(listOf("compact","standard","tall").indexOf(prefs.getString("height","standard")).coerceAtLeast(0))
  heights.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { prefs.edit().putString("height",listOf("compact","standard","tall")[position]).apply() }
  }
  root.addView(heights)
  root.addView(label("Bottom gesture clearance"))
  val clearance=SeekBar(this).apply { max=40;progress=prefs.getInt("clearance",12);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { if(fromUser)prefs.edit().putInt("clearance",value).apply() }
   override fun onStartTrackingTouch(bar:SeekBar?) {}
   override fun onStopTrackingTouch(bar:SeekBar?) {}
  }) }
  root.addView(clearance)
  val enable = Button(this).apply { text="Enable keyboard in Android settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } }
  root.addView(enable)
  val choose = Button(this).apply { text="Choose active keyboard"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() } }
  root.addView(choose)
  root.addView(label("Prototype 0.2 • Keep your existing keyboard available as a fallback."))
  setContentView(ScrollView(this).apply { addView(root) })
 }
}
