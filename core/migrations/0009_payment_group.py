import django.db.models.deletion
from django.db import migrations, models


def backfill_group(apps, schema_editor):
    Payment = apps.get_model('core', 'Payment')
    for payment in Payment.objects.select_related('expense').iterator():
        payment.group_id = payment.expense.group_id_id
        payment.save(update_fields=['group'])


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0008_expenseparticipant_share_amount_and_more'),
    ]

    operations = [
        migrations.AddField(
            model_name='payment',
            name='group',
            field=models.ForeignKey(null=True, on_delete=django.db.models.deletion.CASCADE,
                                    related_name='payments', to='core.group'),
        ),
        migrations.RunPython(backfill_group, migrations.RunPython.noop),
        migrations.AlterField(
            model_name='payment',
            name='group',
            field=models.ForeignKey(on_delete=django.db.models.deletion.CASCADE,
                                    related_name='payments', to='core.group'),
        ),
        migrations.AlterField(
            model_name='payment',
            name='expense',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL,
                                    related_name='payments', to='core.expense'),
        ),
    ]
