from core.models import Activity


def log_activity(actor, action, entity_type, entity_id=None, group=None, **metadata):
    """Record something a user did. `group` makes it visible to that group's members."""
    if group is not None:
        metadata.setdefault('group_name', group.name)
    return Activity.objects.create(
        actor=actor, action=action, entity_type=entity_type, entity_id=entity_id,
        group_id=getattr(group, 'pk', None), metadata=metadata,
    )
