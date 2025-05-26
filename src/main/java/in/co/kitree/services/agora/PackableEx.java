package in.co.kitree.services.agora;


public interface PackableEx extends Packable {
    void unmarshal(ByteBuf in);
}