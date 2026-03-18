package my.hive_back.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.module.user.mapper.UserMapper;
import my.hive_back.module.user.model.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Resource
    private UserMapper userMapper;

    public Long getManagerId(Long applyUserId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, applyUserId);
        queryWrapper.select(User::getManagerId);
        return userMapper.selectOne(queryWrapper).getManagerId();
    }

    public User getUserById(Long applyUserId) {
        return userMapper.selectById(applyUserId);
    }

    public Integer getRoleLevel(Long userId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        queryWrapper.select(User::getRoleLevel);
        return userMapper.selectOne(queryWrapper).getRoleLevel();
    }
}
