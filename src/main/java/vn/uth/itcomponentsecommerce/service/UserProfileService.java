package vn.uth.itcomponentsecommerce.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.ChangePasswordRequest;
import vn.uth.itcomponentsecommerce.dto.ProfileResponse;
import vn.uth.itcomponentsecommerce.dto.UpdateProfileRequest;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.UserRepository;

import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileResponse getProfile(User user) {
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(User user, UpdateProfileRequest req) {
        if (req.getFullName() != null) {
            user.setFullName(req.getFullName().trim().isEmpty() ? null : req.getFullName().trim());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone().trim().isEmpty() ? null : req.getPhone().trim());
        }
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest req) {
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    private static ProfileResponse toResponse(User user) {
        ProfileResponse p = new ProfileResponse();
        p.setId(user.getId());
        p.setUsername(user.getUsername());
        p.setEmail(user.getEmail());
        p.setFullName(user.getFullName());
        p.setPhone(user.getPhone());
        p.setRoles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()));
        return p;
    }
}
