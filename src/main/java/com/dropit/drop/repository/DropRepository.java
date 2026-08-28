package com.dropit.drop.repository;

import com.dropit.drop.entity.Drop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropRepository extends JpaRepository<Drop, Long> {
}
