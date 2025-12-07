package com.pm.kz.project.repository;

import com.pm.kz.project.entity.Trade;
import com.pm.kz.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByUserOrderByExecutedAtDesc(User user);
}