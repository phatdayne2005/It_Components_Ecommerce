package vn.uth.itcomponentsecommerce.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.AuthResponse;
import vn.uth.itcomponentsecommerce.dto.LoginRequest;
import vn.uth.itcomponentsecommerce.dto.RegisterRequest;
import vn.uth.itcomponentsecommerce.entity.Role;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.RoleRepository;
import vn.uth.itcomponentsecommerce.repository.UserRepository;
import vn.uth.itcomponentsecommerce.security.CustomUserDetailsService;
import vn.uth.itcomponentsecommerce.security.JwtService;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            throw new IllegalArgumentException("Username đã tồn tại");
        if (userRepository.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email đã tồn tại");

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        User u = new User();
        u.setUsername(req.getUsername());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setFullName(req.getFullName());
        u.setPhone(req.getPhone());
        u.setEnabled(true);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        u.setRoles(roles);

        userRepository.save(u);

        return buildAuthResponse(u);
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        User u = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalStateException("User vừa auth nhưng không tìm thấy"));

        return buildAuthResponse(u);
    }

    private AuthResponse buildAuthResponse(User u) {
        UserDetails ud = userDetailsService.loadUserByUsername(u.getUsername());
        String token = jwtService.generateToken(ud);
        Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new AuthResponse(token, u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), roleNames);
    }
}
