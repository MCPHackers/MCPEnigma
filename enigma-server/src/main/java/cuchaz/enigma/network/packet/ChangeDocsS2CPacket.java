package cuchaz.enigma.network.packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.network.ClientPacketHandler;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.validation.PrintValidatable;
import cuchaz.enigma.utils.validation.ValidationContext;

public class ChangeDocsS2CPacket implements Packet<ClientPacketHandler> {
	private int syncId;
	private Entry<?> entry;
	private String newDocs;

	ChangeDocsS2CPacket() {
	}

	public ChangeDocsS2CPacket(int syncId, Entry<?> entry, String newDocs) {
		this.syncId = syncId;
		this.entry = entry;
		this.newDocs = newDocs;
	}

	@Override
	public void read(DataInput input) throws IOException {
		this.syncId = input.readUnsignedShort();
		this.entry = PacketHelper.readEntry(input);
		this.newDocs = PacketHelper.readString(input);
	}

	@Override
	public void write(DataOutput output) throws IOException {
		output.writeShort(syncId);
		PacketHelper.writeEntry(output, entry);
		PacketHelper.writeString(output, newDocs);
	}

	@Override
	public void handle(ClientPacketHandler controller) {
		ValidationContext vc = new ValidationContext();
		vc.setActiveElement(PrintValidatable.INSTANCE);

		controller.changeDocs(vc, new EntryReference<>(entry, entry.getName()), newDocs);

		if (!vc.canProceed()) return;
		controller.sendPacket(new ConfirmChangeC2SPacket(syncId));
	}
}
