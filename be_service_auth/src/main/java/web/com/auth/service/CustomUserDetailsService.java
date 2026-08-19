package web.com.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import web.com.auth.entity.RoleEntity;
import web.com.auth.entity.UserEntity;
import web.com.auth.repository.RoleRepository;
import web.com.auth.repository.UserRepository;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserEntity user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new User(user.getUserName(),user.getPassword(), mapRoles(user));
    }

    private Collection<SimpleGrantedAuthority> mapRoles(UserEntity user) {
        List<RoleEntity> roleEntityList = roleRepository.findByUserId(user.getId());
        return roleEntityList.stream().map(role -> new SimpleGrantedAuthority(role.getRoleEnum().name())).toList();
    }
}
