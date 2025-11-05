package LLD.TennisCourt;

class CourtState {
    int courtId;
    int freeAt;
    int usageCount;

    CourtState(int courtId, int freeAt, int usageCount) {
        this.courtId = courtId;
        this.freeAt = freeAt;
        this.usageCount = usageCount;
    }
}
