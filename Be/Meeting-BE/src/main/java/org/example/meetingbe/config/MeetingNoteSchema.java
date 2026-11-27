package org.example.meetingbe.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class MeetingNoteSchema {
    @Bean
    ApplicationRunner allowMultipleMeetingNotes(JdbcTemplate jdbc) {
        return args -> {
            // Add the replacement index before dropping the old index used by the meeting foreign key.
            if (jdbc.queryForList("""
                    select index_name from information_schema.statistics
                    where table_schema = database() and table_name = 'meeting_note'
                    and index_name = 'idx_meeting_note_room_author'
                    """, String.class).isEmpty()) {
                jdbc.execute("create index idx_meeting_note_room_author on meeting_note (meeting_id, author_id)");
            }
            for (String index : jdbc.queryForList("""
                    select index_name from information_schema.statistics
                    where table_schema = database() and table_name = 'meeting_note' and non_unique = 0
                    group by index_name
                    having count(*) = 2 and sum(column_name in ('meeting_id', 'author_id')) = 2
                    """, String.class)) {
                jdbc.execute("alter table meeting_note drop index `" + index.replace("`", "``") + "`");
            }
        };
    }
}
