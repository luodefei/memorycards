package com.example.memorycards.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.R
import com.example.memorycards.data.Api
import com.example.memorycards.data.Session
import org.json.JSONArray
import org.json.JSONObject

class MembershipActivity : AppCompatActivity() {

    private lateinit var levelContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_membership)

        levelContainer = findViewById(R.id.levelContainer)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadLevels()
    }

    private fun loadLevels() {
        Api.request("/api/membership/levels", "GET", null, object : Api.Callback {
            override fun onSuccess(body: String) {
                runCatching { JSONArray(body) }
                    .onSuccess { renderLevels(it) }
                    .onFailure {
                        Toast.makeText(this@MembershipActivity, "响应异常", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onError(code: Int, message: String) {
                Toast.makeText(this@MembershipActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun renderLevels(levels: JSONArray) {
        levelContainer.removeAllViews()
        for (i in 0 until levels.length()) {
            val level = levels.optJSONObject(i) ?: continue
            val item = LayoutInflater.from(this)
                .inflate(R.layout.item_membership_level, levelContainer, false)
            item.findViewById<TextView>(R.id.tvLevelName).text = level.optString("name")
            item.findViewById<TextView>(R.id.tvLevelDesc).text = level.optString("description")
            item.findViewById<TextView>(R.id.tvLevelBonus).text =
                "赠送 ${level.optInt("bonusPoints")} 积分 · ${level.optInt("durationDays")} 天"
            val priceCents = level.optInt("priceCents", 0)
            item.findViewById<TextView>(R.id.tvLevelPrice).text =
                String.format("¥%.2f", priceCents / 100.0)
            item.findViewById<Button>(R.id.btnSubscribe).setOnClickListener {
                subscribe(level.optString("code"), level.optString("name"))
            }
            levelContainer.addView(item)
        }
    }

    private fun subscribe(code: String, name: String) {
        if (!Session.isLoggedIn(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要登录")
                .setMessage("请先登录或注册后再开通会员")
                .setPositiveButton("登录 / 注册") { _, _ ->
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val body = JSONObject().put("levelCode", code)
        Api.request("/api/membership/subscribe", "POST", body.toString(), object : Api.Callback {
            override fun onSuccess(body: String) {
                runCatching { JSONObject(body) }.onSuccess { json ->
                    AlertDialog.Builder(this@MembershipActivity)
                        .setTitle("开通成功")
                        .setMessage(
                            "已开通 $name\n赠送 ${json.optInt("grantedPoints")} 积分\n" +
                                    "当前积分：${json.optInt("points")}"
                        )
                        .setPositiveButton("好的", null)
                        .show()
                }
            }

            override fun onError(code: Int, message: String) {
                Toast.makeText(this@MembershipActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
