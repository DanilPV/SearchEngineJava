package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;


@Entity
@Setter
@Getter
@Table(name = "lemma", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"lemma", "site_id"})
})
public class Lemma  implements Comparable<Lemma>  {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "lemma" )
    private String lemma;

    @Column(name = "frequency")
    private int frequency;



    @Override
    public int compareTo(Lemma other) {
        return this.lemma.compareTo(other.lemma);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return Objects.equals(lemma, lemma1.lemma) &&
                Objects.equals(site, lemma1.site);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lemma, site);
    }

    @Override
    public String toString() {
        return "Lemma{" +
                "id=" + id +
                ", site=" + site +
                ", lemma='" + lemma + '\'' +
                ", frequency=" + frequency +
                '}';
    }

}
