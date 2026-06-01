import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/models/address_model.dart';
import '../address_repository.dart';

/// Manzil qo'shish/tahrirlash formasi. [existing] berilsa — tahrirlash.
class AddressEditScreen extends ConsumerStatefulWidget {
  final Address? existing;
  const AddressEditScreen({super.key, this.existing});

  @override
  ConsumerState<AddressEditScreen> createState() => _AddressEditScreenState();
}

class _AddressEditScreenState extends ConsumerState<AddressEditScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _label;
  late final TextEditingController _line;
  late final TextEditingController _comment;
  bool _saving = false;

  bool get _isEdit => widget.existing != null;

  @override
  void initState() {
    super.initState();
    _label = TextEditingController(text: widget.existing?.label ?? '');
    _line = TextEditingController(text: widget.existing?.addressLine ?? '');
    _comment = TextEditingController(text: widget.existing?.comment ?? '');
  }

  @override
  void dispose() {
    _label.dispose();
    _line.dispose();
    _comment.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final repo = ref.read(addressRepositoryProvider);
    try {
      if (_isEdit) {
        await repo.update(
          widget.existing!.id,
          label: _label.text.trim().isEmpty ? null : _label.text.trim(),
          addressLine: _line.text.trim(),
          comment: _comment.text.trim().isEmpty ? null : _comment.text.trim(),
        );
      } else {
        await repo.create(
          label: _label.text.trim().isEmpty ? null : _label.text.trim(),
          addressLine: _line.text.trim(),
          comment: _comment.text.trim().isEmpty ? null : _comment.text.trim(),
        );
      }
      ref.invalidate(addressesProvider);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_isEdit ? 'Manzilni tahrirlash' : 'Yangi manzil')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _label,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: 'Nom (masalan: Uy, Ish)',
                prefixIcon: Icon(Icons.label_outline),
              ),
            ),
            const SizedBox(height: 14),
            TextFormField(
              controller: _line,
              maxLines: 2,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: 'To\'liq manzil *',
                hintText: 'Ko\'cha, uy, kvartira',
                prefixIcon: Icon(Icons.location_on_outlined),
              ),
              validator: (v) =>
                  (v == null || v.trim().length < 5) ? 'To\'liq manzil kiriting' : null,
            ),
            const SizedBox(height: 14),
            TextFormField(
              controller: _comment,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Izoh (mo\'ljal, qavat, domofon)',
                prefixIcon: Icon(Icons.notes_outlined),
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              child: _saving
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                    )
                  : Text(_isEdit ? 'Saqlash' : 'Qo\'shish'),
            ),
          ],
        ),
      ),
      backgroundColor: AppColors.background,
    );
  }
}
