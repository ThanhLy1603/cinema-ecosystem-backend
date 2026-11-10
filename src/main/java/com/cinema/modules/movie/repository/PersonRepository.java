package com.cinema.modules.movie.repository;

import com.cinema.modules.movie.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    Page<Person> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Person> findAllByIdIn(Collection<Long> personIds);

    List<Person> findAllByOrderByNameAscIdAsc(Pageable pageable);
}
