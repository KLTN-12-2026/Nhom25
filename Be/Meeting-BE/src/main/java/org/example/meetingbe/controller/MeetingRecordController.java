package org.example.meetingbe.controller;

import org.example.meetingbe.dto.MeetingRecordingDto;
import org.example.meetingbe.model.MeetingRecording;
import org.example.meetingbe.repository.IMeetingRecordingRepo;
import org.example.meetingbe.service.meetingRecording.IMeetingRecordingService;
import org.example.meetingbe.service.meetingRecording.MeetingRecordingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/meetingRecord")
@CrossOrigin(origins = "http://localhost:4200/", allowedHeaders = "*")
public class MeetingRecordController {
    @Autowired
    IMeetingRecordingService meetingRecordingService;
    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingRecording> findByMeetingId(@PathVariable("meetingId") Long id){
        MeetingRecording recording = meetingRecordingService.findByMeetingId(id);
        return recording == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(recording);
    }

    @PostMapping("/")
    public MeetingRecording addNewRecord(@RequestBody MeetingRecordingDto meetingRecording){
        System.out.println(meetingRecording.getMeetingId());
        return meetingRecordingService.addOrUpdateMeetingRecord(meetingRecording);
    }
}
