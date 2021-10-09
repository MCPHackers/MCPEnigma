package cuchaz.enigma.network.packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.network.EnigmaServer;
import cuchaz.enigma.network.Message;
import cuchaz.enigma.network.ServerPacketHandler;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.Utils;
import cuchaz.enigma.utils.validation.PrintValidatable;
import cuchaz.enigma.utils.validation.ValidationContext;

public class ChangeDocsC2SPacket implements Packet<ServerPacketHandler> {
	private Entry<?> entry;
	private String newDocs;

	ChangeDocsC2SPacket() {
	}

	public ChangeDocsC2SPacket(Entry<?> entry, String newDocs) {
		this.entry = entry;
		this.newDocs = newDocs;
	}

	@Override
	public void read(DataInput input) throws IOException {
		this.entry = PacketHelper.readEntry(input);
		this.newDocs = PacketHelper.readString(input);
	}

	@Override
	public void write(DataOutput output) throws IOException {
		PacketHelper.writeEntry(output, entry);
		PacketHelper.writeString(output, newDocs);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		ValidationContext vc = new ValidationContext();
		vc.setActiveElement(PrintValidatable.INSTANCE);

		EntryMapping mapping = handler.getServer().getMappings().getDeobfMapping(entry);

		boolean valid = handler.getServer().canModifyEntry(handler.getClient(), entry);
		if (!valid) {
			String oldDocs = mapping == null ? null : mapping.getJavadoc();
			handler.getServer().sendPacket(handler.getClient(), new ChangeDocsS2CPacket(EnigmaServer.DUMMY_SYNC_ID, entry, oldDocs == null ? "" : oldDocs));
			return;
		}

		if (mapping == null) {
			mapping = new EntryMapping(handler.getServer().getMappings().deobfuscate(entry).getName());
		}
		handler.getServer().getMappings().mapFromObf(vc, entry, mapping.withDocs(Utils.isBlank(newDocs) ? null : newDocs));

		if (!vc.canProceed()) return;

		int syncId = handler.getServer().lockEntry(handler.getClient(), entry);
		handler.getServer().sendToAllExcept(handler.getClient(), new ChangeDocsS2CPacket(syncId, entry, newDocs));
		handler.getServer().sendMessage(Message.editDocs(handler.getServer().getUsername(handler.getClient()), entry));
	}
}
