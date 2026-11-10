package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.PersonResponse;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonAdminServiceTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MovieCastRepository movieCastRepository;

    private PersonAdminService personAdminService;

    @BeforeEach
    void setUp() {
        personAdminService = new PersonAdminService(personRepository, movieCastRepository);
    }

    @Test
    void createTrimsNameAndDropsBlankOptionalFields() {
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonResponse personResponse =
                personAdminService.createPerson(new PersonRequest("  Ngô Thanh Vân ", "  ", ""));

        assertEquals("Ngô Thanh Vân", personResponse.name());
        assertNull(personResponse.avatarUrl());
        assertNull(personResponse.bio());
    }

    @Test
    void deleteIsBlockedWhilePersonHasCredits() {
        when(personRepository.findById(5L)).thenReturn(Optional.of(new Person()));
        when(movieCastRepository.existsByPersonId(5L)).thenReturn(true);

        AppException appException = assertThrows(AppException.class, () -> personAdminService.deletePerson(5L));

        assertEquals(HttpStatus.CONFLICT, appException.getStatus());
        assertEquals("PERSON_IN_USE", appException.getErrorCode());
        verify(personRepository, never()).delete(any());
    }

    @Test
    void deleteReportsMissingPerson() {
        when(personRepository.findById(5L)).thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class, () -> personAdminService.deletePerson(5L));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
    }
}
