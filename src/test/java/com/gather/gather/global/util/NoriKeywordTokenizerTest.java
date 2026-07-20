package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NoriKeywordTokenizerTest {

    private final NoriKeywordTokenizer tokenizer = new NoriKeywordTokenizer();

    @Test
    @DisplayName("tokenize splits a compound noun phrase into its dictionary noun components")
    void tokenize_splitsCompoundNounPhrase() {
        List<String> tokens = tokenizer.tokenize("환경정화봉사");

        assertThat(tokens).containsExactly("환경", "정화", "봉사");
    }

    @Test
    @DisplayName("tokenize shares common noun tokens between related keywords")
    void tokenize_sharesTokenBetweenRelatedKeywords() {
        List<String> tokensA = tokenizer.tokenize("환경정화봉사");
        List<String> tokensB = tokenizer.tokenize("환경정화");

        assertThat(tokensA).containsAll(tokensB);
    }

    @Test
    @DisplayName("tokenize returns empty list for blank or null input")
    void tokenize_returnsEmptyList_whenBlankOrNull() {
        assertThat(tokenizer.tokenize("   ")).isEmpty();
        assertThat(tokenizer.tokenize(null)).isEmpty();
    }

    @Test
    @DisplayName(
            "tokenize keeps user-dictionary words whole instead of splitting off the last"
                    + " syllable")
    void tokenize_keepsUserDictionaryWordsWhole() {
        assertThat(tokenizer.tokenize("유기견봉사")).containsExactly("유기견", "봉사");
        assertThat(tokenizer.tokenize("장애인복지관")).contains("장애인");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "청소년쉼터", "해외봉사단", "플로깅", "재활용", "방과후교실", "저소득층", "재가복지", "길고양이", "요양보호사", "사회복지사",
                "마을공동체", "밑반찬배달", "다문화가족"
            })
    @DisplayName(
            "tokenize keeps additional user-dictionary compound words whole instead of losing"
                    + " their meaning to default segmentation")
    void tokenize_keepsAdditionalUserDictionaryCompoundsWhole(String word) {
        assertThat(tokenizer.tokenize(word)).containsExactly(word);
    }
}
