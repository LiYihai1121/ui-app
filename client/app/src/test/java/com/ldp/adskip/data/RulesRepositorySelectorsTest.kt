package com.ldp.adskip.data

import com.ldp.adskip.engine.RuleSet
import com.ldp.adskip.engine.selector.SelectorParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RulesRepositorySelectorsTest {
    @Test
    fun `selector channel remains non-empty and compilable`() {
        val expr = "[text*=\"跳过\"] [click=\"true\"]"
        val parsed = SelectorParser.parse(expr)
        assertNotNull(parsed)

        val rules = RuleSet(
            keywords = emptyList(),
            viewIds = emptyList(),
            selectors = listOf(parsed!!),
            disabled = false,
            schemaVersion = 1
        )

        assertFalse(rules.isEmpty)
        assertEquals(1, rules.selectors.size)
    }
}
