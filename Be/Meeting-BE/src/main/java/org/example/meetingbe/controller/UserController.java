package org.example.meetingbe.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.mail.MessagingException;
import org.example.meetingbe.dto.*;
import org.example.meetingbe.model.*;
import org.example.meetingbe.dto.LoginForm;
import org.example.meetingbe.dto.Register;
import org.example.meetingbe.dto.UserDto;
import org.example.meetingbe.repository.IRoleRepo;
import org.example.meetingbe.repository.IUserRepo;
import org.example.meetingbe.response.JwtResponse;
import org.example.meetingbe.response.ResponseMessage;
import org.example.meetingbe.security.jwt.JwtTokenProvider;
import org.example.meetingbe.security.userpricipal.UserPrinciple;
import org.example.meetingbe.service.mailSender.MailRegister;
import org.example.meetingbe.service.cache.CacheService;
import org.example.meetingbe.service.user.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.example.meetingbe.model.User;
import org.example.meetingbe.service.user.IUserService;
import org.example.meetingbe.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:4200/", allowedHeaders = "*")
public class UserController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserController.class);
    @Autowired
    private CacheService cacheService;
    @Autowired
    private IRoleRepo roleRepo;
    @Autowired
    private IUserService userService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Value("${google.client-id}")
    private String googleClientId;
    @Autowired
    private IUserRepo userRepo;
    @Autowired
    private MailRegister mailRegister;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Register register) throws MessagingException {
        if (userService.exitsByUsername(register.getUserName()) || userService.exitsByEmail(register.getEmail())) {
            return new ResponseEntity<>(new ResponseMessage("Tài khoản hoặc Email đã tồn tại"), HttpStatus.CONFLICT);
        }
        userService.register(register);
        return new ResponseEntity<>(new ResponseMessage("Tạo tài khoản thành công"), HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginForm login) {
        if (login.getUserName() == null || login.getUserName().isBlank()
                || login.getPassword() == null || login.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body(new ResponseMessage("Vui lòng nhập tài khoản và mật khẩu"));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login.getUserName().trim(), login.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtTokenProvider.generateToken(authentication);
            UserPrinciple userPrinciple = (UserPrinciple) authentication.getPrincipal();
            LocalDateTime time = LocalDateTime.now();
            return new ResponseEntity<>(new JwtResponse(token, userPrinciple.getUsername(), userPrinciple.getAuthorities(), time), HttpStatus.OK);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return new ResponseEntity<>(new ResponseMessage("Tài khoản hoặc mật khẩu không đúng"), HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            logger.error("Login processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Lỗi xử lý đăng nhập trên server. Vui lòng kiểm tra log backend"));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginOrRegisterWithGoogle(@RequestBody Map<String, String> body) {
        String idTokenString = body.get("idToken");

        GoogleIdToken idToken = verifyGoogleToken(idTokenString);
        if (idToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        // Kiểm tra user
        Optional<User> optionalUser = userService.findByEmail(email);
        Boolean existsEmail = userService.exitsByEmail(email);
        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            if (!"google".equals(user.getProvider())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ResponseMessage("Email này đã được đăng ký bằng tài khoản mật khẩu"));
            }
        }
        Set<Role> role = roleRepo.findByRoleName("USER");
        if (optionalUser.isEmpty()) {
            user = new User();
            user.setAvatar(picture);
            user.setEmail(email);
            user.setUserName(name);
            user.setPassword("");
            user.setProvider("google");
            user.setVip(false);
            user.setRoles(role);
            user = userRepo.save(user);
        } else {
            user = optionalUser.get();
        }
        Optional<User> userAfterSave = userService.findByEmail(email);
        try {
            UserPrinciple userPrinciple = new UserPrinciple(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(userPrinciple, null, userPrinciple.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtTokenProvider.generateToken(authentication);

            LocalDateTime time = LocalDateTime.now();

            return ResponseEntity.ok(
                    new JwtResponse(token, userAfterSave.get().getUserName(),
                            userAfterSave.get().getRoles().stream()
                                    .map(role1 -> new SimpleGrantedAuthority(role1.getRoleName()))
                                    .collect(Collectors.toList()),
                            time)
            );
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage("Tài khoản hoặc mật khẩu không đúng"));
        }
    }

    private GoogleIdToken verifyGoogleToken(String idTokenString) {
        try {
            HttpTransport transport = new NetHttpTransport();
            JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            return verifier.verify(idTokenString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @PutMapping("/delete")
    public User deleteUser(@RequestParam("id") Long id) {
        return userService.deleteUser(id);
    }


    // Lấy thông tin người dùng theo ID
    @GetMapping("/")
    public User getUserById(@RequestParam Long id) {
        return userService.getUserById(id);
    }

    // Cập nhật người dùng
    @PutMapping("/")
    public User updateUser(@RequestParam Long id, @RequestBody UserDto userDTO) {
        return userService.updateUser(id, userDTO);
    }

    // Đếm tổng số người dùng trong năm
    @GetMapping("/count")
    public long countTotalUsers(@RequestParam("year") int year) {
        return userService.countTotalUsers(year);
    }

    // Đếm số người dùng VIP
    @GetMapping("/count/vip")
    public long countVipUsers() {
        return userService.countVipUsers();
    }
    @GetMapping("/vip")
    public List<User> getVipUsers() {
        return userService.getVipUsers();
    }
    @GetMapping("/normal")
    public List<User> getNormalUsers() {
        return userService.getNormalUsers();
    }
    @GetMapping("/years")
    public List<Integer> getRegistrationYears() {
        return userService.getAllYears();
    }
    @GetMapping("/monthly-registrations/{year}")
    public List<MonthlyUserCountDTO> getMonthlyUserRegistrations(@PathVariable("year") int year) {
        return userService.getUserRegistrationsByYear(year);
    }
    @GetMapping("/getByYear")
    public List<User> getByYear(@RequestParam(name = "year") int year) {
        return userService.getAllByYear(year);
    }

    @GetMapping("/getByUserName/{userName}")
    public ResponseEntity<?> getProfile(@PathVariable("userName") String userName) {
        if (userService.getByUsername(userName) == null) {
            return new ResponseEntity(new ResponseMessage("Find not found user"), HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(userService.getByUsername(userName));
    }

    @PutMapping("/updateProfile/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable("id") Long id, @RequestBody UserEditTO userDto) {
        userService.updateProfile(id, userDto);
        return new ResponseEntity<>(new ResponseMessage("Update success"), HttpStatus.OK);
    }

    @GetMapping("/getPageUser")
    public ResponseEntity<Page<User>> getPageUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createAt,asc") String[] sort
    ) {
        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));
        Page<User> users = userService.findBy(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/getUserStatusTrue")
    public ResponseEntity<Page<User>> getUserStatusTrue(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            @RequestParam(name = "sort", defaultValue = "createAt,desc") String[] sort
    ) {
        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Page<User> contacts = userService.getAllByStatusTrue(pageable);
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/getUserStatusFalse")
    public ResponseEntity<Page<User>> getUserStatusFalse(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            @RequestParam(name = "sort", defaultValue = "createAt,desc") String[] sort
    ) {
        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Page<User> contacts = userService.getAllByStatusFalse(pageable);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam("email") String email) {
        email = email.trim();
        if (!userService.exitsByEmail(email)) {
            return ResponseEntity.badRequest().body(new ResponseMessage("Email không tồn tại"));
        }
        String otp = cacheService.generateOtp(email);
        try {
            mailRegister.sendOtp(email, otp);
        } catch (MessagingException | org.springframework.mail.MailException e) {
            cacheService.clearOtp(email);
            logger.error("Failed to send password reset email", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ResponseMessage("Không gửi được OTP. Vui lòng kiểm tra cấu hình email của server"));
        }
        return ResponseEntity.ok("OTP đã được gửi về mail của bạn");
    }

    @PostMapping("/verify-otp")
    public synchronized ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        if (email == null || email.isBlank() || otp == null || !otp.matches("[0-9]{4}")) {
            return ResponseEntity.badRequest().body(new ResponseMessage("OTP không được để trống"));
        }
        email = email.trim();
        String cachedOtp = cacheService.getOtp(email);
        if (otp != null && cachedOtp != null && cachedOtp.equals(otp)) {
            cacheService.clearOtp(email);
            return ResponseEntity.ok(Map.of("message", "Xác minh OTP thành công",
                    "resetToken", cacheService.issueResetToken(email)));
        }
        return ResponseEntity.badRequest().body(new ResponseMessage("OTP không khớp"));
    }

    @PutMapping("/resetPassword")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        if (email == null || email.isBlank() || password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(new ResponseMessage("Email và mật khẩu tối thiểu 8 ký tự là bắt buộc"));
        }
        try {
            if (!cacheService.consumeResetToken(email.trim(), request.get("resetToken"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseMessage("Phiên đặt lại mật khẩu hết hạn hoặc không hợp lệ. Vui lòng xác minh OTP lại"));
            }
            userService.resetPassword(password, email.trim());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.badRequest().body(new ResponseMessage("Email không tồn tại. Vui lòng thực hiện lại bước quên mật khẩu"));
        } catch (Exception e) {
            logger.error("Password reset failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Không lưu được mật khẩu. Vui lòng thực hiện lại bước quên mật khẩu"));
        }
        return ResponseEntity.ok(new ResponseMessage("Thay đổi mật khẩu thành công"));
    }

}
