package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "`index`")
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "lemma_id")
    private Lemma lemma;

    @ManyToOne
    @JoinColumn(name = "page_id")
    private Page page;

    @Column(name = "`rank`" )
    private float rank;


    @Override
    public String toString() {
        return "Index{" +
                "id=" + id +
                ", lemma=" + lemma +
                ", page=" + page +
                ", rank=" + rank +
                '}';
    }
}
