package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.dto.response.PersonOptionResponse;
import com.cinema.modules.movie.dto.response.PersonResponse;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.repository.MovieCastRepository;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
import com.cinema.modules.movie.util.MovieMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersonAdminService implements IPersonAdminService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_OPTIONS = 2000;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PersonRepository personRepository;
    private final MovieCastRepository movieCastRepository;

    public PersonAdminService(PersonRepository personRepository, MovieCastRepository movieCastRepository) {
        this.personRepository = personRepository;
        this.movieCastRepository = movieCastRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<PersonResponse> searchPersons(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by("name", "id"));
        String name = keyword == null ? "" : keyword.trim();
        Page<Person> personPage = personRepository.findByNameContainingIgnoreCase(name, pageable);
        return PageResponse.of(personPage, personPage.getContent().stream().map(MovieMapper::toPersonResponse).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<PersonOptionResponse> listPersonOptions() {
        return personRepository.findAllByOrderByNameAscIdAsc(PageRequest.of(0, MAX_OPTIONS)).stream()
                .map(person -> new PersonOptionResponse(person.getId(), person.getName()))
                .toList();
    }

    @Transactional
    @Override
    public PersonResponse createPerson(PersonRequest personRequest) {
        Person person = new Person();
        applyRequest(person, personRequest);
        return MovieMapper.toPersonResponse(personRepository.save(person));
    }

    @Transactional
    @Override
    public PersonResponse updatePerson(Long personId, PersonRequest personRequest) {
        Person person = loadPerson(personId);
        applyRequest(person, personRequest);
        return MovieMapper.toPersonResponse(personRepository.save(person));
    }

    @Transactional
    @Override
    public void deletePerson(Long personId) {
        Person person = loadPerson(personId);
        if (movieCastRepository.existsByPersonId(personId)) {
            throw new AppException(
                    "Nghệ sĩ đang tham gia phim, hãy gỡ khỏi các phim trước khi xóa", HttpStatus.CONFLICT, "PERSON_IN_USE");
        }
        personRepository.delete(person);
    }

    private Person loadPerson(Long personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new AppException("Không tìm thấy nghệ sĩ", HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND"));
    }

    private void applyRequest(Person person, PersonRequest personRequest) {
        person.setName(personRequest.name().trim());
        person.setAvatarUrl(blankToNull(personRequest.avatarUrl()));
        person.setBio(blankToNull(personRequest.bio()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
