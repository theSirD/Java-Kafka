package ru.isaev;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.isaev.Requests.Request;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
}
