import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


def backfill_owner(apps, schema_editor):
    """Existing groups are owned by their earliest member."""
    Group = apps.get_model('core', 'Group')
    UserGroup = apps.get_model('core', 'UserGroup')
    for group in Group.objects.filter(created_by__isnull=True):
        first = UserGroup.objects.filter(group_id=group.pk).order_by('id').first()
        if first:
            group.created_by_id = first.user_id_id
            group.save(update_fields=['created_by'])


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0009_payment_group'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AddField(
            model_name='group',
            name='icon',
            field=models.CharField(blank=True, default='', max_length=16),
        ),
        migrations.AddField(
            model_name='group',
            name='created_by',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL,
                                    related_name='created_groups', to=settings.AUTH_USER_MODEL),
        ),
        migrations.RunPython(backfill_owner, migrations.RunPython.noop),
    ]
