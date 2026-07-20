package com.gather.gather.domain.posting.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
