package com.example.memorycards.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.memorycards.data.Api
import com.example.memorycards.data.Session
import org.json.JSONObject

object PremiumGate {

    /** 高级功能入口：未登录引导登录；已登录非会员给升级/购买/签到/分享入口；会员放行。 */
    fun onPremiumFeature(activity: Activity, onAllowed: () -> Unit) {
        if (!Session.isLoggedIn(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("需要登录")
                .setMessage("深度研究为会员功能，请先登录或注册")
                .setPositiveButton("登录 / 注册") { _, _ ->
                    activity.startActivity(Intent(activity, LoginActivity::class.java))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        Api.request(activity, "/api/membership/current", "GET", null, object : Api.Callback {
            override fun onSuccess(body: String) {
                // 204 无内容 = 未开通会员
                val member = body.isNotBlank() &&
                        runCatching { JSONObject(body).optBoolean("active", false) }.getOrDefault(false)
                if (member) {
                    onAllowed()
                } else {
                    showPointsOptions(activity)
                }
            }

            override fun onError(code: Int, message: String) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showPointsOptions(activity: Activity) {
        val options = arrayOf("升级会员", "购买积分", "签到领积分", "分享领积分")
        AlertDialog.Builder(activity)
            .setTitle("会员功能")
            .setMessage("深度研究需要开通会员，你也可以先通过签到、分享攒积分")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> activity.startActivity(Intent(activity, MembershipActivity::class.java))
                    1 -> buyPoints(activity)
                    2 -> checkin(activity)
                    3 -> share(activity)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buyPoints(activity: Activity) {
        val packs = arrayOf("100 积分 - ¥6", "500 积分 - ¥25", "1000 积分 - ¥45")
        val points = intArrayOf(100, 500, 1000)
        AlertDialog.Builder(activity)
            .setTitle("购买积分")
            .setItems(packs) { _, which ->
                val body = JSONObject().put("points", points[which])
                Api.request(activity, "/api/points/purchase", "POST", body.toString(), object : Api.Callback {
                    override fun onSuccess(body: String) = toastGranted(activity, body)
                    override fun onError(code: Int, message: String) {
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkin(activity: Activity) {
        Api.request(activity, "/api/points/checkin", "POST", "{}", object : Api.Callback {
            override fun onSuccess(body: String) = toastGranted(activity, body)
            override fun onError(code: Int, message: String) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun share(activity: Activity) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "我在用「记忆卡片」背知识点，推荐给你～")
        }
        activity.startActivity(Intent.createChooser(send, "分享记忆卡片"))
        Api.request(activity, "/api/points/share", "POST", "{}", object : Api.Callback {
            override fun onSuccess(body: String) = toastGranted(activity, body)
            override fun onError(code: Int, message: String) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun toastGranted(activity: Activity, body: String) {
        runCatching { JSONObject(body) }.onSuccess { json ->
            Toast.makeText(
                activity,
                "到账 ${json.optInt("granted", 0)} 积分，当前 ${json.optInt("points", 0)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}