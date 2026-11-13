package com.mavworks.service;

import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BlockService {
    public enum Result { BLOCKED, ALREADY_BLOCKED }
    public record VisibleUser(Long id, String username) {}
    private final CurrentUser current;
    private final UserRepository users;
    private final UserBlockRepository blocks;

    public BlockService(CurrentUser current, UserRepository users, UserBlockRepository blocks) {
        this.current = current;
        this.users = users;
        this.blocks = blocks;
    }

    @Transactional
    public Result blockUser(Long targetId) {
        var actor = current.username().flatMap(users::findForBlock)
                .orElseThrow(() -> new AccessDeniedException("Sign in to block an account."));
        if (targetId == null || targetId.equals(actor.getId())) {
            throw new IllegalArgumentException("Select another user to block.");
        }
        // The actor lock makes repeated and concurrent confirmations idempotent.
        if (blocks.existsByBlockerIdAndBlockedId(actor.getId(), targetId)) return Result.ALREADY_BLOCKED;
        var target = users.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Account not available."));
        blocks.saveAndFlush(actor.blockUser(target));
        return Result.BLOCKED;
    }

    public Optional<VisibleUser> visibleUser(Long id) {
        var viewer = current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to view accounts."));
        if (id == null || blocks.findHiddenUserIds(viewer.getId()).contains(id)) return Optional.empty();
        return users.findById(id).map(user -> new VisibleUser(user.getId(), user.getUsername()));
    }

    public Optional<VisibleUser> getUserEvenIfBlocked(Long id){
        return users.findById(id).map(user -> new VisibleUser(user.getId(), user.getUsername()));
    }

    public boolean isNotVisible(Long targetId){
        var viewer = current.username().flatMap(users::findByUsername)
            .orElseThrow(() -> new AccessDeniedException("Failed to load options. Sign in to load"));
        return blocks.existsByBlockerIdAndBlockedId(viewer.getId(), targetId);
    }

    @Transactional
    public void unblockUser(Long targetId) {
        var actor = current.username().flatMap(users::findForBlock)
                .orElseThrow(() -> new AccessDeniedException("Sign in to unblock an account."));
        if (targetId == null || targetId.equals(actor.getId())) {
            throw new IllegalArgumentException("Select another user to unblock.");
        }

        if (blocks.existsByBlockerIdAndBlockedId(actor.getId(), targetId)){
            users.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Account not available."));
            java.util.List<String> stringIn = blocks.findByBlockerIdAndBlockedId(actor.getId(), targetId);
            Long in = Long.parseLong(stringIn.get(0));
            if(in != null){
                blocks.deleteById(in);
            }
        }
    }
}
