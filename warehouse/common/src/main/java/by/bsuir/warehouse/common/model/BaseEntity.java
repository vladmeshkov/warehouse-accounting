package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Базовый абстрактный класс для всех сущностей предметной области.
 * Все сущности имеют уникальный идентификатор.
 */
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    protected int id;

    public BaseEntity() {}

    public BaseEntity(int id) {
        this.id = id;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity)) return false;
        BaseEntity that = (BaseEntity) o;
        return id == that.id && this.getClass() == that.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getClass());
    }
}
