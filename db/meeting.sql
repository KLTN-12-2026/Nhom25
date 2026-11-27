-- Nhom25 / Meeting-BE schema from commit 4e5ce7e.
-- Run once on an empty schema.
CREATE DATABASE IF NOT EXISTS `meeting` CHARACTER SET utf8mb4;
USE `meeting`;

    create table chat_messages (
        id bigint not null auto_increment,
        timestamp datetime(6) not null,
        message varchar(255) not null,
        room_id varchar(255) not null,
        sender_id varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table contact (
        date_send DATE,
        status BIT default 0,
        id bigint not null auto_increment,
        user_id bigint,
        content TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table meeting (
        is_active BIT default 0,
        co_host_id bigint,
        create_at DATETIME,
        creator_id bigint,
        end_time DATETIME,
        host_id bigint,
        id bigint not null auto_increment,
        roles_version bigint,
        start_time DATETIME,
        code varchar(50),
        title varchar(50),
        primary key (id)
    ) engine=InnoDB;

    create table meeting_role_history (
        actor_id bigint,
        created_at datetime(6),
        id bigint not null auto_increment,
        meeting_id bigint not null,
        new_co_host_id bigint,
        new_host_id bigint,
        previous_co_host_id bigint,
        previous_host_id bigint,
        action varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table meeting_recording (
        id bigint not null auto_increment,
        meeting_id bigint,
        upload_at DATETIME,
        record_url TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table meeting_summaries (
        create_at DATETIME,
        id bigint not null auto_increment,
        meeting_id bigint,
        summary TEXT,
        transcript LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table participants (
        removed_from_meeting bit,
        id bigint not null auto_increment,
        join_at DATETIME,
        left_at DATETIME,
        meeting_id bigint,
        user_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table payment (
        status BIT default 0,
        total DOUBLE,
        create_at DATETIME,
        id bigint not null auto_increment,
        user_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table reward_account (
        balance integer not null,
        first_milestone_claimed bit not null,
        lifetime_points integer not null,
        meeting_count integer not null,
        second_milestone_claimed bit not null,
        user_id bigint not null,
        primary key (user_id)
    ) engine=InnoDB;

    create table reward_attendance (
        credited bit not null,
        credited_date date,
        connected_at datetime(6),
        id bigint not null auto_increment,
        meeting_id bigint,
        seconds bigint not null,
        user_id bigint,
        meeting_code varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table reward_claim (
        points_spent integer not null,
        created_at datetime(6),
        gift_id bigint,
        id bigint not null auto_increment,
        user_id bigint,
        gift_name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table reward_entry (
        points integer not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        user_id bigint,
        description varchar(255),
        meeting_code varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table reward_gift (
        active bit not null,
        cost integer not null,
        vip_days integer not null,
        id bigint not null auto_increment,
        description varchar(255),
        name varchar(255),
        type varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table reward_rules (
        daily_limit integer not null,
        first_daily_bonus integer not null,
        first_milestone integer not null,
        first_milestone_points integer not null,
        meeting_points integer not null,
        minimum_minutes integer not null,
        second_milestone integer not null,
        second_milestone_points integer not null,
        id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table role (
        id bigint not null auto_increment,
        role_name varchar(20),
        primary key (id)
    ) engine=InnoDB;

    create table schedule (
        create_at DATETIME,
        id bigint not null auto_increment,
        schedule_time DATETIME,
        user_id bigint,
        description TEXT,
        title varchar(50),
        primary key (id)
    ) engine=InnoDB;

    create table streak_logs (
        checkin_date date not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table streak_reward (
        is_active BIT,
        reward_value integer not null,
        streak_days integer not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        description TEXT,
        reward_name varchar(150) not null,
        reward_type varchar(50) not null,
        primary key (id)
    ) engine=InnoDB;

    create table user (
        birthday DATE,
        gender integer,
        is_vip BIT default 0,
        status BIT,
        create_at DATETIME,
        id bigint not null auto_increment,
        reward_vip_expires_at datetime(6),
        address TEXT,
        avater TEXT,
        email varchar(50),
        first_name varchar(50),
        last_name varchar(50),
        password varchar(255),
        phone varchar(11),
        provider varchar(20),
        user_name varchar(50),
        primary key (id)
    ) engine=InnoDB;

    create table user_role (
        role_id bigint not null,
        user_id bigint not null,
        primary key (role_id, user_id)
    ) engine=InnoDB;

    create table user_streak (
        current_streak integer not null,
        last_checkin_date date,
        longest_streak integer not null,
        total_points integer not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    alter table reward_attendance 
       add constraint UKqkdam8ig9hf5f13aisb50kk49 unique (user_id, meeting_id);

    alter table streak_logs 
       add constraint UKecghurku0k4dcy5by5enw4bc unique (user_id, checkin_date);

    alter table streak_reward 
       add constraint UKpkjqvfqtpihdgrotr1l5amhyb unique (streak_days);

    alter table user_streak 
       add constraint UKkx1k3rt9gyg52yigt0ofnq9bb unique (user_id);

    alter table contact 
       add constraint FKe07k4jcfdophemi6j1lt84b61 
       foreign key (user_id) 
       references user (id);

    alter table meeting 
       add constraint FKdl48iralcu7ubrd4gqn8367o7 
       foreign key (co_host_id) 
       references user (id);

    alter table meeting 
       add constraint FKlq4em79qaqn39cdgkbcexnsfr 
       foreign key (creator_id) 
       references user (id);

    alter table meeting 
       add constraint FK4h7sf34l85homeohg7a56cspq 
       foreign key (host_id) 
       references user (id);

    alter table meeting_role_history 
       add constraint FK6t02q96aer9udkm7xyn5yl67b 
       foreign key (meeting_id) 
       references meeting (id);

    alter table meeting_recording 
       add constraint FKek2ct3hmgd8793bllqg1b4eqv 
       foreign key (meeting_id) 
       references meeting (id);

    alter table meeting_summaries 
       add constraint FKv70bt560lhxxtlbuxewerejb 
       foreign key (meeting_id) 
       references meeting (id);

    alter table participants 
       add constraint FKe8yai8s8movfspwlgqdjor9hj 
       foreign key (meeting_id) 
       references meeting (id);

    alter table participants 
       add constraint FKlygocmohuh11svfduxxw8ykk6 
       foreign key (user_id) 
       references user (id);

    alter table payment 
       add constraint FK4spfnm9si9dowsatcqs5or42i 
       foreign key (user_id) 
       references user (id);

    alter table schedule 
       add constraint FKa50n59y1j4a6qwa42p8jiguds 
       foreign key (user_id) 
       references user (id);

    alter table streak_logs 
       add constraint FKghgiv5apr8migmk9hgin0a71u 
       foreign key (user_id) 
       references user (id);

    alter table user_role 
       add constraint FKa68196081fvovjhkek5m97n3y 
       foreign key (role_id) 
       references role (id);

    alter table user_role 
       add constraint FK859n2jvi8ivhui0rl0esws6o 
       foreign key (user_id) 
       references user (id);

    alter table user_streak 
       add constraint FK9fr5pmf61g1ghiys083q1upki 
       foreign key (user_id) 
       references user (id);

-- Personal notes.
CREATE TABLE IF NOT EXISTS `meeting_note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `meeting_id` BIGINT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `title` VARCHAR(160) NOT NULL DEFAULT '',
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_meeting_note_room_author` (`meeting_id`, `author_id`),
    CONSTRAINT `fk_meeting_note_meeting` FOREIGN KEY (`meeting_id`) REFERENCES `meeting` (`id`),
    CONSTRAINT `fk_meeting_note_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB;

INSERT INTO `role` (`role_name`) VALUES ('ADMIN'), ('USER');
