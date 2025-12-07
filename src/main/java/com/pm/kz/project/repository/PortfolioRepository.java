package com.pm.kz.project.repository;

import com.pm.kz.project.entity.Portfolio;
import com.pm.kz.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUser(User user);
    Optional<Portfolio> findByUserAndSymbol(User user, String symbol);
}