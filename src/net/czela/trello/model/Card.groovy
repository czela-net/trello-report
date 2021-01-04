package net.czela.trello.model

class Card {
    String id, name, userName, typeName, shortLink, desc
    Long budget = 0L
    boolean approved = false
    Long userId, akceId, typeId, statusId

    @Override
    String toString() {
        return "Card{" +
                "id='" + id + '\'' +
                ", akceId=" + akceId +
                ", name='" + name + '\'' +
                ", budget=" + budget +
                ", approved=" + approved +
                ", statusId=" + statusId +
                ", userName='" + userName + '\'' +
                ", userId=" + userId +
                ", typeName='" + typeName + '\'' +
                ", typeId=" + typeId +
                '}';
    }
}
