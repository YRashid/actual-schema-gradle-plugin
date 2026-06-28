package io.github.yrashid.actualschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PgDumpNormalizerTest {
    private val normalizer = PgDumpNormalizer()

    @Test
    fun `removes volatile pg dump lines`() {
        val dump = """
            -- PostgreSQL database dump
            -- Dumped from database version 18.1
            -- Dumped by pg_dump version 18.1
            \restrict random-key
            CREATE TABLE public.widget (id bigint);
            \unrestrict random-key

        """.trimIndent()

        assertEquals(
            "-- PostgreSQL database dump\nCREATE TABLE public.widget (id bigint);\n",
            normalizer.normalize(dump)
        )
    }

    @Test
    fun `groups index-like blocks by target table`() {
        val dump = """
            -- PostgreSQL database dump

            --
            -- Name: beta; Type: TABLE; Schema: public; Owner: -
            --

            CREATE TABLE public.beta (
                id bigint NOT NULL,
                code text
            );


            --
            -- Name: alpha; Type: TABLE; Schema: public; Owner: -
            --

            CREATE TABLE public.alpha (
                id bigint NOT NULL,
                code text
            );


            --
            -- Name: alpha alpha_pkey; Type: CONSTRAINT; Schema: public; Owner: -
            --

            ALTER TABLE ONLY public.alpha
                ADD CONSTRAINT alpha_pkey PRIMARY KEY (id);


            --
            -- Name: beta beta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
            --

            ALTER TABLE ONLY public.beta
                ADD CONSTRAINT beta_pkey PRIMARY KEY (id);


            --
            -- Name: idx_alpha_id; Type: INDEX; Schema: public; Owner: -
            --

            CREATE INDEX idx_alpha_id ON public.alpha USING btree (id);


            --
            -- Name: idx_beta_id; Type: INDEX; Schema: public; Owner: -
            --

            CREATE INDEX idx_beta_id ON public.beta USING btree (id);


            --
            -- Name: alpha alpha_code_key; Type: CONSTRAINT; Schema: public; Owner: -
            --

            ALTER TABLE ONLY public.alpha
                ADD CONSTRAINT alpha_code_key UNIQUE (code);


            --
            -- Name: idx_alpha_code; Type: INDEX; Schema: public; Owner: -
            --

            CREATE UNIQUE INDEX idx_alpha_code ON public.alpha USING btree (code);


            --
            -- Name: beta fk_beta_alpha; Type: FK CONSTRAINT; Schema: public; Owner: -
            --

            ALTER TABLE ONLY public.beta
                ADD CONSTRAINT fk_beta_alpha FOREIGN KEY (id) REFERENCES public.alpha(id);
        """.trimIndent()

        val normalized = normalizer.normalize(dump)

        assertBefore(normalized, "CREATE TABLE public.alpha", "ADD CONSTRAINT beta_pkey")
        assertBefore(normalized, "ADD CONSTRAINT beta_pkey", "CREATE INDEX idx_beta_id")
        assertBefore(normalized, "CREATE INDEX idx_beta_id", "ADD CONSTRAINT alpha_pkey")
        assertBefore(normalized, "ADD CONSTRAINT alpha_pkey", "CREATE INDEX idx_alpha_id")
        assertBefore(normalized, "CREATE INDEX idx_alpha_id", "ADD CONSTRAINT alpha_code_key")
        assertBefore(normalized, "ADD CONSTRAINT alpha_code_key", "CREATE UNIQUE INDEX idx_alpha_code")
        assertBefore(normalized, "CREATE UNIQUE INDEX idx_alpha_code", "ADD CONSTRAINT fk_beta_alpha")
    }

    private fun assertBefore(text: String, first: String, second: String) {
        val firstIndex = text.indexOf(first)
        val secondIndex = text.indexOf(second)

        assertTrue(firstIndex >= 0, "Missing '$first' in text")
        assertTrue(secondIndex >= 0, "Missing '$second' in text")
        assertTrue(firstIndex < secondIndex, "Expected '$first' before '$second'")
    }
}
