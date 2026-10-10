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
  val enable = Button(this).apply { text="Enable keyboard in Android settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } }
  root.addView(enable)
  val choose = Button(this).apply { text="Choose active keyboard"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() } }
  root.addView(choose)
  root.addView(label("Prototype 0.1 • Keep your existing keyboard available as a fallback."))
  setContentView(root)
 }
}
