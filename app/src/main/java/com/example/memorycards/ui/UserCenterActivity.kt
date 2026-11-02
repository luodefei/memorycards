package com.example.memorycards.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.R
import com.example.memorycards.data.Api
import com.example.memorycards.data.Session
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserCenterActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var loginButtons: LinearLayout
    private lateinit var actionsCard: View
    private lateinit var tvPointsValue: TextView
    private lateinit var tvMemberValue: TextView
    private lateinit var btnLogout: Button

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_center)

        tvName = findViewById(R.id.tvName)
        tvStatus = findViewById(R.id.tvStatus)
        loginButtons = findViewById(R.id.loginButtons)
        actionsCard = findViewById(R.id.actionsCard)
        tvPointsValue = findViewById(R.id.tvPointsValue)
        tvMemberValue = findViewById(R.id.tvMemberValue)
        btnLogout = findViewById(R.id.btnLogout)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<Button>(R.id.btnGoLogin).setOnClickListener { openLogin(false) }
        findViewById<Button>(R.id.btnGoRegister).setOnClickListener { openLogin(true) }

        findViewById<View>(R.id.rowCheckin).setOnClickListener { if (requireLogin()) doCheckin() }
        findViewById<View>(R.id.rowShare).setOnClickListener { if (requireLogin()) doShare() }
        findViewById<View>(R.id.rowBuy).setOnClickListener { if (requireLogin()) showBuyDialog() }
        findViewById<View>(R.id.rowMembership).setOnClickListener { if (requireLogin()) openMembership() }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定退出当前账号？")
                .setPositiveButton("退出") { _, _ ->
                    Session.clear(this)
                    renderLoggedOut()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (!Session.isLoggedIn(this)) {
            renderLoggedOut()
            return
        }
        Api.request("/api/users/me", "GET", null, object : Api.Callback {
            override fun onSuccess(body: String) {
                runCatching { JSONObject(body) }
                    .onSuccess { renderUser(it) }
                    .onFailure { renderLoggedOut() }
            }

            override fun onError(code: Int, message: String) {
                if (code == 401) {
                    Session.clear(this@UserCenterActivity)
                    renderLoggedOut()
                } else {
                    Toast.makeText(this@UserCenterActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun renderLoggedOut() {
        tvName.text = "未登录"
        tvStatus.text = "登录后可使用会员与积分功能"
        loginButtons.visibility = View.VISIBLE
        actionsCard.visibility = View.GONE
        btnLogout.visibility = View.GONE
    }

    private fun renderUser(user: JSONObject) {
        tvName.text = user.optString("username")
        tvPointsValue.text = "${user.optInt("points", 0)}"

        val member = user.optJSONObject("membership")
        if (member != null && member.optBoolean("active", false)) {
            val end = dateFormat.format(Date(member.optLong("endAt")))
            tvMemberValue.text = "${member.optString("levelName")} · $end 到期"
            tvStatus.text = "开通会员解锁深度研究等专属功能"
        } else {
            tvMemberValue.text = "未开通"
            tvStatus.text = "开通会员解锁深度研究等专属功能"
        }

        loginButtons.visibility = View.GONE
        actionsCard.visibility = View.VISIBLE
        btnLogout.visibility = View.VISIBLE
    }

    private fun openLogin(register: Boolean) {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.EXTRA_REGISTER, register)
        })
    }

    private fun openMembership() {
        startActivity(Intent(this, MembershipActivity::class.java))
    }

    private fun requireLogin(): Boolean {
        if (Session.isLoggedIn(this)) return true
        AlertDialog.Builder(this)
            .setTitle("需要登录")
            .setMessage("请先登录或注册后再使用该功能")
            .setPositiveButton("登录 / 注册") { _, _ -> openLogin(false) }
            .setNegativeButton("取消", null)
            .show()
        return false
    }

    private fun doCheckin() {
        Api.request("/api/points/checkin", "POST", "{}", object : Api.Callback {
            override fun onSuccess(body: String) = showGranted(body)
            override fun onError(code: Int, message: String) {
                Toast.makeText(this@UserCenterActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun doShare() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "我在用「记忆卡片」背知识点，推荐给你～")
        }
        startActivity(Intent.createChooser(send, "分享记忆卡片"))
        Api.request("/api/points/share", "POST", "{}", object : Api.Callback {
            override fun onSuccess(body: String) = showGranted(body)
            override fun onError(code: Int, message: String) {
                Toast.makeText(this@UserCenterActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showBuyDialog() {
        val packs = arrayOf("100 积分 - ¥6", "500 积分 - ¥25", "1000 积分 - ¥45")
        val points = intArrayOf(100, 500, 1000)
        AlertDialog.Builder(this)
            .setTitle("购买积分")
            .setItems(packs) { _, which ->
                val body = JSONObject().put("points", points[which])
                Api.request("/api/points/purchase", "POST", body.toString(), object : Api.Callback {
                    override fun onSuccess(body: String) = showGranted(body)
                    override fun onError(code: Int, message: String) {
                        Toast.makeText(this@UserCenterActivity, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGranted(body: String) {
        runCatching { JSONObject(body) }.onSuccess { json ->
            val granted = json.optInt("granted", 0)
            val total = json.optInt("points", 0)
            tvPointsValue.text = "$total"
            Toast.makeText(this, "到账 $granted 积分，当前 $total", Toast.LENGTH_SHORT).show()
        }
    }
}
