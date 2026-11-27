package org.example.meetingbe.service.meetingAi;

import org.example.meetingbe.repository.MeetingNoteRepository.HistoryNote;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class MeetingNoteSearch {
    private static final int EXCERPT_LENGTH = 1400;
    private static final int EXCERPT_STEP = 1200;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> COMMON_WORDS = Set.of(
            "ban", "bao", "cho", "choi", "chu", "cua", "cuoc", "dang", "duoc", "ghi",
            "gi", "hay", "hoi", "hop", "khong", "minh", "nao", "nhung", "noi", "the",
            "theo", "tiep", "toi", "tom", "tat", "trong", "truoc", "tu", "vao", "ve", "voi");

    private MeetingNoteSearch() {}

    public record Excerpt(String meetingTitle, String content) {}
    private record Match(Excerpt excerpt, int score) {}

    public static List<Excerpt> find(String question, List<HistoryNote> history) {
        Set<String> queryTerms = words(question);
        boolean requestsNotes = queryTerms.containsAll(Set.of("ghi", "chu"));
        queryTerms.removeAll(COMMON_WORDS);
        boolean recentNotesRequested = requestsNotes && queryTerms.isEmpty();
        List<Match> matches = new ArrayList<>();
        for (HistoryNote note : history) {
            String content = note.getContent();
            if (content.isBlank()) continue;
            String title = note.getTitle() == null ? "" : note.getTitle();
            Set<String> titleTerms = words(title);
            Match best = null;
            for (int start = 0; start < content.length(); start += EXCERPT_STEP) {
                int end = Math.min(start + EXCERPT_LENGTH, content.length());
                String excerpt = content.substring(start, end);
                Set<String> terms = words(excerpt);
                terms.addAll(titleTerms);
                terms.retainAll(queryTerms);
                int score = terms.size();
                if (best == null || score > best.score()) {
                    best = new Match(new Excerpt(title, excerpt), score);
                }
                if (end == content.length()) break;
            }
            if (best != null && (best.score() > 0 || recentNotesRequested)) matches.add(best);
        }
        return matches.stream().sorted(Comparator.comparingInt(Match::score).reversed())
                .limit(4).map(Match::excerpt).toList();
    }

    private static Set<String> words(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = MARKS.matcher(normalized).replaceAll("").replace('đ', 'd');
        Set<String> words = new HashSet<>();
        for (String word : SEPARATORS.split(normalized)) {
            if (word.length() > 1) words.add(word);
        }
        return words;
    }
}
