package com.ldp.adskip.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SafetyGuard JVM 单测：验证黑名单/合法性护栏。
 */
class SafetyGuardTest {

    @Test
    fun `self package blocked`() {
        val node = FakeAdNode.node(text = "跳过")
        assertFalse(SafetyGuard.canClick(node, "com.ldp.adskip"))
    }

    @Test
    fun `deny word in text blocked`() {
        val node = FakeAdNode.node(text = "确认支付")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word in desc blocked`() {
        val node = FakeAdNode.node(text = "跳过", desc = "点击授权")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 付款 blocked`() {
        val node = FakeAdNode.node(text = "立即付款")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 同意 blocked`() {
        val node = FakeAdNode.node(text = "同意并继续")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 登录 blocked`() {
        val node = FakeAdNode.node(text = "登录")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 购买 blocked`() {
        val node = FakeAdNode.node(text = "购买会员")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `deny word 安装 blocked`() {
        val node = FakeAdNode.node(text = "安装应用")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `invisible node blocked`() {
        val node = FakeAdNode.node(text = "跳过", visible = false)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `zero width bounds blocked`() {
        val node = FakeAdNode.node(text = "跳过", width = 0)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `zero height bounds blocked`() {
        val node = FakeAdNode.node(text = "跳过", height = 0)
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `valid node allowed`() {
        val node = FakeAdNode.node(text = "跳过", width = 100, height = 50)
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `skip text not blocked`() {
        val node = FakeAdNode.node(text = "跳过广告")
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `english skip not blocked`() {
        val node = FakeAdNode.node(text = "Skip")
        assertTrue(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `english deny word blocked`() {
        val node = FakeAdNode.node(text = "Install Now")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `english deny word case insensitive blocked`() {
        val node = FakeAdNode.node(text = "ALLOW")
        assertFalse(SafetyGuard.canClick(node, "com.example.app"))
    }

    @Test
    fun `sensitive system packages blocked`() {
        val node = FakeAdNode.node(text = "跳过")
        assertFalse(SafetyGuard.canClick(node, "com.android.permissioncontroller"))
        assertFalse(SafetyGuard.canClick(node, "com.android.packageinstaller"))
        assertFalse(SafetyGuard.canClick(node, "com.android.settings"))
    }

    @Test
    fun `deny word in parent chain blocked`() {
        // 无标签图标按钮 + 敏感文案在父容器：只看目标自身会被绕过
        val button = FakeAdNode.node()
        val dialog = FakeAdNode.node(text = "要允许安装此应用吗？", children = listOf(button))
        val target = dialog.children().first()
        assertFalse(SafetyGuard.canClick(target, "com.example.app"))
    }

    @Test
    fun `unlabeled button without sensitive ancestors allowed`() {
        val button = FakeAdNode.node()
        val card = FakeAdNode.node(text = "限时秒杀", children = listOf(button))
        assertTrue(SafetyGuard.canClick(card.children().first(), "com.example.app"))
    }

    @Test
    fun `package guard blocks self and sensitive system packages`() {
        // 坐标点击（自定义取点）也必须过这份包级护栏
        assertFalse(SafetyGuard.canClickPackage("com.ldp.adskip"))
        assertFalse(SafetyGuard.canClickPackage("com.android.settings"))
        assertFalse(SafetyGuard.canClickPackage("com.android.permissioncontroller"))
        assertFalse(SafetyGuard.canClickPackage("com.android.packageinstaller"))
        assertTrue(SafetyGuard.canClickPackage("com.example.app"))
    }
}
