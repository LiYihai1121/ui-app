package com.ldp.adskip.engine

import org.junit.Assert.*
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
}
