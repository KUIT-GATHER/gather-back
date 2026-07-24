package com.gather.gather.global.util;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

/**
 * Nori(mecab-ko-dic) 형태소 분석기로 검색어에서 명사(일반명사 NNG, 고유명사 NNP)만 추출한다. "유기견봉사" -> ["유기견", "봉사"] 처럼 쪼개서,
 * 서로 다른 검색어라도 같은 명사를 공유하면 추천검색어 집계에서 합산되게 한다.
 *
 * <p>Lucene {@link org.apache.lucene.analysis.Analyzer}는 재사용을 전제로 스레드 안전하게 설계되어 있어서, 싱글톤 빈인 이 클래스에서
 * 인스턴스를 한 번만 만들어 재사용한다(호출마다 새로 만들면 불필요한 오버헤드가 생긴다).
 */
@Component
public class NoriKeywordTokenizer implements AutoCloseable {

    private static final int MIN_TOKEN_LENGTH = 2;
    private static final Set<POS.Tag> NOUN_TAGS = Set.of(POS.Tag.NNG, POS.Tag.NNP);
    private static final String USER_DICTIONARY_RESOURCE = "/nori/userdict.txt";

    private final KoreanAnalyzer analyzer = createAnalyzer();

    /**
     * 기본 mecab-ko-dic은 "유기견"/"장애인" 같은 단어의 마지막 음절을 접미사(XSN 등)로 분리해버려서 "유기"/"장애"로 잘린다. 사용자 사전에 등록된
     * 단어는 분해되지 않는 하나의 명사로 우선 인식되므로, {@value #USER_DICTIONARY_RESOURCE}에 등록해 온전히 보존한다.
     */
    private static KoreanAnalyzer createAnalyzer() {
        InputStream resource =
                NoriKeywordTokenizer.class.getResourceAsStream(USER_DICTIONARY_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException(
                    "Nori 사용자 사전 리소스를 찾을 수 없습니다: " + USER_DICTIONARY_RESOURCE);
        }
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            UserDictionary userDictionary = UserDictionary.open(reader);
            return new KoreanAnalyzer(
                    userDictionary,
                    KoreanTokenizer.DEFAULT_DECOMPOUND,
                    KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                    false);
        } catch (IOException e) {
            throw new UncheckedIOException("Nori 사용자 사전 로드 실패: " + USER_DICTIONARY_RESOURCE, e);
        }
    }

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        try (TokenStream tokenStream = analyzer.tokenStream("keyword", new StringReader(text))) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            PartOfSpeechAttribute posAttribute =
                    tokenStream.addAttribute(PartOfSpeechAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                String term = termAttribute.toString();
                if (term.length() >= MIN_TOKEN_LENGTH
                        && NOUN_TAGS.contains(posAttribute.getLeftPOS())) {
                    tokens.add(term);
                }
            }
            tokenStream.end();
        } catch (IOException e) {
            throw new IllegalStateException("검색어 형태소 분석 실패: " + text, e);
        }
        return tokens;
    }

    @PreDestroy
    @Override
    public void close() {
        analyzer.close();
    }
}
