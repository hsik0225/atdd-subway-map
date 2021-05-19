package wooteco.subway.domain;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import wooteco.subway.exception.EntityNotFoundException;
import wooteco.subway.exception.InvalidRequestException;

public class Sections {
    private final List<Section> sections;

    public Sections() {
        this(new ArrayList<>());
    }

    public Sections(Section... sections) {
        this(Arrays.asList(sections));
    }

    public Sections(List<Section> sections) {
        this.sections = new ArrayList<>(sections);
    }

    public List<Section> getSections() {
        return sections;
    }

    public Sections insert(Section newSection) {
        if (sections.isEmpty()) {
            return new Sections(newSection);
        }

        validateSectionToInsert(newSection);

        if (shouldInsertAtTop(newSection)) {
            return insertAtTop(newSection);
        }

        if (shouldInsertAtBottom(newSection)) {
            return insertAtBottom(newSection);
        }

        if (isStationInUpStations(newSection.getUpStation())) {
            return insertAtUpStationOfMiddle(newSection);
        }

        return insertAtDownStationOfMiddle(newSection);
    }

    private void validateSectionToInsert(final Section newSection) {
        final Predicate<Section> upStationPredicate = section -> section.isUpStationEquals(newSection.getUpStation())
                || section.isDownStationEquals(newSection.getUpStation());
        final boolean existsUpStation = sections.stream()
                                                .anyMatch(upStationPredicate);

        final Predicate<Section> downStationPredicate = section -> section.isUpStationEquals(newSection.getDownStation())
                || section.isDownStationEquals(newSection.getDownStation());
        final boolean existsDownStation = sections.stream()
                                                  .anyMatch(downStationPredicate);

        if (!(existsUpStation || existsDownStation)) {
            throw new InvalidRequestException("적어도 구간의 하나의 역은 이미 다른 구간에 저장되어 있어야 합니다.");
        }

        if (existsUpStation && existsDownStation) {
            throw new InvalidRequestException("이미 저장되어 있는 구간입니다.");
        }
    }

    public boolean shouldInsertAtTop(Section newSection) {
        final boolean isTopStation = isTopStation(newSection.getDownStation());
        final boolean isDownStationInDownStations = isStationInDownStations(newSection.getDownStation());
        return isTopStation && !isDownStationInDownStations;
    }

    public boolean isTopStation(Station station) {
        return !isStationInDownStations(station) && isStationInUpStations(station);
    }

    private boolean isStationInDownStations(Station station) {
        return findByDownStation(station).isPresent();
    }

    private Optional<Section> findByDownStation(Station station) {
        return sections.stream()
                       .filter(section -> section.isDownStationEquals(station))
                       .findAny();
    }

    private Sections insertAtTop(Section newSection) {
        sections.add(0, newSection);
        return new Sections(sections);
    }

    public boolean shouldInsertAtBottom(Section section) {
        final boolean isUpStationInUpStations = isStationInUpStations(section.getUpStation());
        return isBottomStation(section.getUpStation()) && !isUpStationInUpStations;
    }

    public boolean isBottomStation(Station station) {
        return !isStationInUpStations(station) && isStationInDownStations(station);
    }

    public boolean isStationInUpStations(final Station station) {
        return findByUpStation(station).isPresent();
    }

    public Optional<Section> findByUpStation(Station station) {
        return sections.stream()
                       .filter(section -> section.isUpStationEquals(station))
                       .findAny();
    }

    private Sections insertAtBottom(Section newSection) {
        sections.add(sections.size() - 1, newSection);
        return new Sections(sections);
    }

    private Sections insertAtUpStationOfMiddle(Section newSection) {
        final Section oldSection = findByUpStation(newSection.getUpStation()).get();
        if (oldSection.getDistance() <= newSection.getDistance()) {
            throw new InvalidRequestException("추가하려는 구간의 길이는 기존 구간의 길이보다 작아야 합니다.");
        }
        final int distance = oldSection.getDistance() - newSection.getDistance();
        final Function<Section, Section> updateFunction = section -> section.updateUpStation(newSection.getDownStation())
                                                                            .updateDistance(distance);
        return insertAtMiddle(oldSection, updateFunction, newSection);
    }

    private Sections insertAtDownStationOfMiddle(Section newSection) {
        final Section oldSection = findByDownStation(newSection.getDownStation()).get();
        if (oldSection.getDistance() <= newSection.getDistance()) {
            throw new InvalidRequestException("추가하려는 구간의 길이는 기존 구간의 길이보다 작아야 합니다.");
        }
        final int distance = oldSection.getDistance() - newSection.getDistance();

        final Function<Section, Section> updateFunction = section -> section.updateDownStation(newSection.getUpStation())
                                                                            .updateDistance(distance);
        return insertAtMiddle(oldSection, updateFunction, newSection);
    }

    private Sections insertAtMiddle(Section oldSection, Function<Section, Section> updateFunction, Section newSection) {
        sections.remove(oldSection);
        sections.add(updateFunction.apply(oldSection));
        sections.add(newSection);
        return new Sections(sections);
    }

    public List<Station> getSortedStations() {
        if (sections.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Station, Station> sectionMap = makeSectionMap();
        Station station = findTopStation(sectionMap);

        List<Station> sortedStations = new ArrayList<>();
        sortedStations.add(station);
        for (int i = 0, size = sectionMap.size(); i < size; i++) {
            station = sectionMap.get(station);
            sortedStations.add(station);
        }

        return sortedStations;
    }

    private Map<Station, Station> makeSectionMap() {
        Map<Station, Station> sectionMap = new HashMap<>();
        for (Section section : sections) {
            sectionMap.put(section.getUpStation(), section.getDownStation());
        }
        return sectionMap;
    }

    private Station findTopStation(Map<Station, Station> sectionMap) {
        for (Station upStation : sectionMap.keySet()) {
            if (sectionMap.values().stream().noneMatch(downStation -> downStation.equals(upStation))) {
                return upStation;
            }
        }

        throw new EntityNotFoundException("상행 종점역이 존재하지 않습니다.");
    }

    public Sections deleteStation(Station station) {
        validateSectionToDelete();

        if (isTopStation(station) || isBottomStation(station)) {
            return deleteStationWithoutUpdate(station);
        }

        return deleteStationWithUpdate(station);
    }

    private void validateSectionToDelete() {
        if (sections.isEmpty()) {
            throw new InvalidRequestException("구간이 존재하지 않는 노선입니다.");
        }

        final boolean hasOnlyOneSection = sections.size() == 1;
        if (hasOnlyOneSection) {
            throw new InvalidRequestException("하나의 구간만 존재할 경우 삭제할 수 없습니다.");
        }
    }

    private Sections deleteStationWithoutUpdate(Station station) {
        final Section sectionContainingStation = findByUpStation(station)
                .orElseThrow(() -> new EntityNotFoundException("해당 역과 일치하는 구간이 존재하지 않습니다."));

        sections.remove(sectionContainingStation);
        return new Sections(sections);
    }

    private Sections deleteStationWithUpdate(Station station) {
        final Section sectionContainByUpStation = findByUpStation(station)
                .orElseThrow(() -> new EntityNotFoundException("해당 역과 일치하는 구간이 존재하지 않습니다."));

        final Section sectionContainByDownStation = findByDownStation(station)
                .orElseThrow(() -> new EntityNotFoundException("해당 역과 일치하는 구간이 존재하지 않습니다."));

        sections.remove(sectionContainByUpStation);
        sections.remove(sectionContainByDownStation);

        int distance = sectionContainByUpStation.getDistance() + sectionContainByDownStation.getDistance();
        final Section updatedSection = sectionContainByDownStation.updateDownStation(sectionContainByUpStation.getDownStation())
                                                                  .updateDistance(distance);
        sections.add(updatedSection);
        return new Sections(sections);
    }
}