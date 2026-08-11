package cn.lcxqy.starfree.user;

class VerificationMailException extends RuntimeException {
    enum Kind {
        CONFIGURATION,
        AUTHENTICATION,
        CONNECTION,
        BUSY,
        DELIVERY
    }

    private final Kind kind;

    VerificationMailException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    VerificationMailException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind getKind() {
        return kind;
    }
}
