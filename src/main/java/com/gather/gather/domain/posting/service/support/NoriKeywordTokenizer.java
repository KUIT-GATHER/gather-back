package com.gather.gather.domain.posting.service.support;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.POS;
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

    private final KoreanAnalyzer analyzer = new KoreanAnalyzer();

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
