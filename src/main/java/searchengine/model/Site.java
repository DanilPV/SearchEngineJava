package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.stereotype.Component;
import searchengine.enums.STATUS;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;


@Component
@Entity
@Setter
@Getter
@Table(name = "site")
public class Site implements Comparable<Site> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)


    @Column(name = "status")
    private STATUS status;

    @CreationTimestamp
    @Column(name = "status_time")
    private LocalDateTime statusTime;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "url")
    private String url;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Page> pages;

    @Transient
    private boolean scanLemmas;

    @Transient
    private String mainURL;


    @Override
    public int compareTo(Site other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Site site1 = (Site) o;
        return Objects.equals(name, site1.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }


    @Override
    public String toString() {
        return "Site{" +
                "id=" + id +
                ", status=" + status +
                ", statusTime=" + statusTime +
                ", lastError='" + lastError + '\'' +
                ", url='" + url + '\'' +
                ", name='" + name + '\'' +
                ", pages=" + pages +
                ", scanLemmas=" + scanLemmas +
                ", mainURL='" + mainURL + '\'' +
                '}';
    }
}
